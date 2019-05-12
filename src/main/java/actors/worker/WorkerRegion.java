package actors.worker;

import actors.iot.SensorData;
import actors.master.MasterWorkerProtocol;
import akka.actor.*;
import akka.cluster.client.ClusterClient;
import akka.cluster.client.ContactPoints;
import akka.cluster.client.SubscribeContactPoints;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import akka.pattern.Patterns;
import akka.routing.FromConfig;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class WorkerRegion extends AbstractActor {

    private String regionId;
    private ActorRef clusterClient;
    private final ActorRef workerRegion;
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props(String regionId, ActorRef clusterClient) {
        return Props.create(WorkerRegion.class, regionId, clusterClient);
    }

    public WorkerRegion(String regionId, final ActorRef clusterClient) {
        this.regionId = regionId;
        this.clusterClient = clusterClient;
        ActorSystem system = getContext().getSystem();
        ClusterShardingSettings settings = ClusterShardingSettings.create(system);

        // Create router
        ActorRef workProcessorRouter = getContext().actorOf(

                FromConfig.getInstance()
                        .withSupervisorStrategy(supervisorStrategy()) // This will treat each routee independently
                        .props(Props.create(WorkProcessor.class)),
                "workProcessorRouter");

        workerRegion = ClusterSharding.get(system)
                .start(
                        "workers",
                        Worker.props(clusterClient, workProcessorRouter),
                        settings,
                        new WorkRegionMessageExtractor(10));
        registerRegionToMaster();
    }

    private void registerRegionToMaster() {
        log.info("Sending registration to master ...");
        Duration timeout = Duration.ofSeconds(10);
        CompletionStage<Object> future = Patterns.ask(clusterClient, new ClusterClient.SendToAll("/user/master/singleton", new MasterWorkerProtocol.RegisterWorkerRegion(this.regionId, self())), timeout);
        future.thenAccept(result -> {
            if (result instanceof MasterWorkerProtocol.WorkerRegionAck) {
                log.info("Received register ack form Master : {}", result.getClass().getName());
                // Subscribe to cluster client events (contact points)
                clusterClient.tell(SubscribeContactPoints.getInstance(), self());
            } else {
                log.info("Unknown message received from Master : {}", result.getClass().getName());
            }
        }).exceptionally(e ->
                {
                    // Retry registration
                    log.warning("No registration Ack from Master, instead got exception : {}", e.getCause().getMessage());
                    log.warning("Retrying ...");
                    registerRegionToMaster();
                    return null;
                }
        );
    }

    // Supervision for all children of WorkerRegion (workProcessorRouter)
    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(
                DeciderBuilder
                        .match(ActorInitializationException.class, e -> SupervisorStrategy.restart())
                        .matchAny(this::handleAnySupervisor)
                        .build());
    }

    private SupervisorStrategy.Directive handleAnySupervisor(Throwable throwable) {
        return SupervisorStrategy.restart();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(SensorData.class, this::handleWork)
                .match(ContactPoints.class, this::handleContactPoints)
                .match(MasterWorkerProtocol.RecoverWorkerRegion.class, this::handleRecoverFromMaster)
                .matchAny(this::handleAny)
                .build();
    }

    private void handleRecoverFromMaster(MasterWorkerProtocol.RecoverWorkerRegion recoverWorkerRegion) {
        sender().tell(recoverWorkerRegion, self());
    }

    private void handleWork(SensorData sensorData) {
        // Delegate sensorData to the shard of workers
        log.info("WorkerRegion >>> Worker Region {} : Received sensorData : {}", regionId, sensorData.getDataId());
        workerRegion.tell(sensorData, getSelf());
    }

    private void handleContactPoints(ContactPoints contactPoints) {
        log.info("WorkerRegion >>> Contact points : {}", contactPoints.contactPoints());
    }

    private void handleAny(Object o) {
        log.info("WorkerRegion >>> HANDLE ANY - Received unknown object of class : {}", o.getClass().getName());
    }
}
