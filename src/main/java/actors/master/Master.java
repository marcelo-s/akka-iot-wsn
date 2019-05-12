package actors.master;

import actors.iot.SensorData;
import akka.actor.*;
import akka.cluster.client.ClusterClientReceptionist;
import akka.cluster.client.ClusterClients;
import akka.cluster.client.SubscribeClusterClients;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.Patterns;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Deadline;
import scala.concurrent.duration.FiniteDuration;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public class Master extends AbstractPersistentActor {

    static String RESULTS_TOPIC = "results-topic";
    private static final MasterWorkerProtocol.WorkerRegionAck WORKER_REGION_ACK = new MasterWorkerProtocol.WorkerRegionAck();
    private static final MasterWorkerProtocol.RecoverWorkerRegion RECOVER_WORKER_REGION =
            new MasterWorkerProtocol.RecoverWorkerRegion();

    private final FiniteDuration workTimeout;
    private final ActorRef mediator;
    private final WorkerRegionState workerRegionState;
    private final SensorDataState sensorDataState;
    private final Cancellable cleanUpTask;
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props(FiniteDuration workTimeout) {
        return Props.create(Master.class, workTimeout);
    }

    public Master(FiniteDuration workTimeout) {
        this.mediator = DistributedPubSub.get(getContext().system()).mediator();
        // Extension is loaded on configuration file
        // Register Master as a receptionist
        ClusterClientReceptionist.get(getContext().system()).registerService(self());
        this.workerRegionState = new WorkerRegionState();
        this.sensorDataState = new SensorDataState();
        this.workTimeout = workTimeout;
        this.cleanUpTask = scheduleWorkerReceptionCheck();
        subscribeToClusterClientEvents();
    }

    private void subscribeToClusterClientEvents() {
        ClusterClientReceptionist clusterClientReceptionist = ClusterClientReceptionist.get(getContext().getSystem());
        ActorRef underlyingReceptionist = clusterClientReceptionist.underlying();
        underlyingReceptionist.tell(SubscribeClusterClients.getInstance(), self());
    }

    @Override
    public void postStop() {
        super.postStop();
        cleanUpTask.cancel();
    }

    private Cancellable scheduleWorkerReceptionCheck() {
        ActorSystem system = getContext().getSystem();
        final FiniteDuration initialDelay = this.workTimeout.div(2);
        final FiniteDuration interval = this.workTimeout.div(2);
        final ExecutionContext ec = system.dispatcher();
        return system.scheduler().schedule(
                initialDelay,
                interval,
                () -> self().tell(new MasterProtocol.WorkerReceptionCheck(), self()),
                ec);
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .match(SensorDataEvent.class, this::handleSensorDataEventRecover)
                .match(WorkerRegionEvent.class, this::handleWorkerRegionEventEventRecover)
                .match(RecoveryCompleted.class, this::onRecoveryCompleted)
                .build();
    }

    private void onRecoveryCompleted(RecoveryCompleted recoveryCompleted) {
        log.info("Cluster Master >>> RECOVERY COMPLETED");
        log.info("Cluster Master >>> Worker Regions number: " + workerRegionState.getWorkerRegionsCount());
        checkRegionsAvailability();
    }

    private void checkRegionsAvailability() {
        Set<ActorRef> workerRegions = workerRegionState.getWorkerRegions();
        java.time.Duration timeout = java.time.Duration.ofSeconds(10);

        // Warning! Do not use sender() or actor state in callbacks
        workerRegions.forEach(workerRegion -> {
            log.info("Cluster Master >>> Checking Worker region availability for worker region: {}", workerRegion);
            CompletionStage<Object> recoverRegionCS = Patterns.ask(workerRegion, RECOVER_WORKER_REGION, timeout);
            recoverRegionCS
                    .thenAccept(result -> {
                        getContext().watch(workerRegion);
                        log.info("Cluster Master >>> Recovered region : {}", workerRegion.path());
                    })
                    .exceptionally(e ->
                            {
                                log.warning("Cluster Master >>> Could not recover region : {} REMOVING!", workerRegion.path());
                                self().tell(
                                        new MasterWorkerProtocol.RemoveUnrecoverableWorkerRegion(workerRegion),
                                        self());
                                return null;
                            }
                    );
        });
    }

    private void handleWorkerRegionEventEventRecover(WorkerRegionEvent workerRegionEvent) {
        workerRegionState.updateState(workerRegionEvent);
        log.info("Cluster Master >>> Replayed Worker Region event: {}", workerRegionEvent.getClass().getName());
    }

    private void handleSensorDataEventRecover(SensorDataEvent sensorDataEvent) {
        sensorDataState.updateState(sensorDataEvent);
        log.info("Cluster Master >>> Replayed Sensor Data event: {}", sensorDataEvent.getClass().getName());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(MasterProtocol.WorkerReceptionCheck.class, this::handleWorkerReceptionCheck)
                .match(MasterIoTProtocol.SensorDataForProcess.class, this::handleSensorData)
                .match(MasterWorkerProtocol.SensorDataReceived.class, this::handleSensorDataReceived)
                .match(MasterWorkerProtocol.RegisterWorkerRegion.class, this::handleRegisterWorkerRegion)
                .match(MasterWorkerProtocol.ProcessingCompleted.class, this::handleProcessingCompleted)
                .match(MasterWorkerProtocol.RemoveUnrecoverableWorkerRegion.class, this::handleUnrecoverableWorkerRegion)
                .match(Terminated.class, this::handleWorkerRegionTerminated)
                .match(ClusterClients.class, this::handleClusterClients)
                .matchAny(this::handleAny)
                .build();
    }

    private void handleUnrecoverableWorkerRegion(MasterWorkerProtocol.RemoveUnrecoverableWorkerRegion unrecoverable) {
        persist(new WorkerRegionEvent.Unregistered(unrecoverable.getWorkerRegion()), this::handleUnregisteredWorkerRegionEvent);
    }

    // Check for sensorData acknowledgements by workers
    private void handleWorkerReceptionCheck(MasterProtocol.WorkerReceptionCheck workerReceptionCheck) {
        sensorDataState.getWorkerReceptionTimeouts().entrySet().removeIf(entrySet -> {
            String dataId = entrySet.getKey();
            Deadline receivedDeadline = entrySet.getValue();
            if (receivedDeadline.isOverdue()) {
                log.info("Cluster Master >>> Timeout of reception - No ack from Worker for SensorData Id {}", dataId);
                persist(new SensorDataEvent.DataTimeoutExpired(dataId), this::handleDataTimedOutEvent);
                return true;
            }
            return false;
        });
    }

    private void handleSensorData(MasterIoTProtocol.SensorDataForProcess sensorDataForProcess) {
        SensorData sensorData = sensorDataForProcess.getSensorData();
        if (sensorDataState.wasReceived((sensorData.getDataId()))) {
            sender().tell(new MasterIoTProtocol.SensorDataReceived(sensorData.getDataId()), self());
        } else {
            log.info("Cluster Master >>> Received SensorData Id {}", sensorData.getDataId());
            persist(new SensorDataEvent.DataAccepted(sensorData), this::handleDataAcceptedEvent);
        }
    }

    private void handleDataAcceptedEvent(SensorDataEvent.DataAccepted dataAccepted) {
        sensorDataState.updateState(dataAccepted);
        // Send acknowledge back to the IoT system
        sender().tell(new MasterIoTProtocol.SensorDataReceived(dataAccepted.getSensorData().getDataId()), self());
        sendDataToWorkers();
    }

    private void handleSensorDataReceived(MasterWorkerProtocol.SensorDataReceived sensorDataReceived) {
        // Remove timeout when sensor data is received by the worker
        persist(new SensorDataEvent.DataReceivedByWorker(sensorDataReceived.getDataId()), this::handleSensorDataReceivedEvent);
    }

    private void handleSensorDataReceivedEvent(SensorDataEvent.DataReceivedByWorker dataReceivedByWorker) {
        sensorDataState.updateState(dataReceivedByWorker);
    }

    private void handleProcessingCompleted(MasterWorkerProtocol.ProcessingCompleted processingCompleted) {
        if (sensorDataState.isCompleted(processingCompleted.getSensorDataId())) {
            // In case completed message is received again
            log.warning("Cluster Master >>> Resending ack of processing completed  of SensorData Id: {} to Worker : {}",
                    processingCompleted.getSensorDataId(),
                    processingCompleted.getWorkerId());
            sender().tell(new MasterWorkerProtocol.ProcessingCompletedAck(processingCompleted.getSensorDataId()), self());
        } else if (!sensorDataState.isInProgress(processingCompleted.getSensorDataId())) {
            log.warning("Cluster Master >>> SensorData NOT IN PROGRESS BUT IS FINISHED! SensorData Id {} | Worker {}",
                    processingCompleted.getSensorDataId(),
                    processingCompleted.getWorkerId());
        } else {
            log.info("Cluster Master >>> Acknowledged result processing for SensorData Id {} and Worker {}",
                    processingCompleted.getSensorDataId(),
                    processingCompleted.getWorkerId());
            sender().tell(new MasterWorkerProtocol.ProcessingCompletedAck(processingCompleted.getSensorDataId()), self());
            persist(new SensorDataEvent.DataProcessed(processingCompleted.getSensorDataId(), processingCompleted.getResult()), this::handleDataProcessed);
        }
    }

    private void handleClusterClients(ClusterClients clusterClients) {
        log.info("Cluster Master >>> Updated clients");
        // handle Clients
    }

    private void handleAny(Object o) {
        log.warning("Cluster Master >>> HANDLE ANY-Received unknown object of class : {}", o.getClass().getName());
    }

    private void handleWorkerRegionTerminated(Terminated terminated) {
        log.warning("Cluster Master >>> Worker Region Terminated : {}", terminated.getActor().path());
        persist(new WorkerRegionEvent.Unregistered(terminated.getActor()), this::handleUnregisteredWorkerRegionEvent);
    }

    private void handleUnregisteredWorkerRegionEvent(WorkerRegionEvent.Unregistered unregisteredEvent) {
        workerRegionState.updateState(unregisteredEvent);
        log.warning("Cluster Master >>> Worker Region unregistered : {}", unregisteredEvent.getWorkerRegion().path());
    }

    private void handleDataTimedOutEvent(SensorDataEvent.DataTimeoutExpired dataTimeoutExpired) {
        sensorDataState.updateState(dataTimeoutExpired);
        sendDataToWorkers();
    }

    private void sendDataToWorkers() {
        if (sensorDataState.hasPendingSensorData()) {
            System.out.println("Size of pending data: " + sensorDataState.getPendingSensorData());
            Optional<ActorRef> potentialWorkerRegion = workerRegionState.getAvailableWorkerRegion();
            if (potentialWorkerRegion.isPresent()) {
                ActorRef workerRegion = potentialWorkerRegion.get();
                SensorData nextSensorData = sensorDataState.nextSensorData();
                SensorDataEvent.DataSentForProcess dataSentForProcess = new SensorDataEvent.DataSentForProcess(nextSensorData.getDataId());
                persist(dataSentForProcess, (dataSentForProcessEvent) -> this.handleDataSentForProcessEvent(dataSentForProcessEvent, nextSensorData, workerRegion));
            } else {
                log.warning("Cluster Master >>> THERE ARE NO WORK REGIONS TO SEND THE WORK");
            }
        }
    }

    private void handleDataSentForProcessEvent(SensorDataEvent.DataSentForProcess dataSentForProcess, SensorData nextSensorData, ActorRef workerRegion) {
        SensorData sensorDataToStart = sensorDataState.updateState(dataSentForProcess);
        assert sensorDataToStart.getDataId().equals(nextSensorData.getDataId());
        log.info("Cluster Master >>> Sent data to Worker, SensorData ID : {}",
                sensorDataToStart.getDataId());
        workerRegion.tell(nextSensorData, self());
    }

    private void handleRegisterWorkerRegion(MasterWorkerProtocol.RegisterWorkerRegion workerRegion) {
        ActorRef workerRegionActorRef = workerRegion.getRegionActorRef();
        String workerRegionId = workerRegion.getWorkerRegionId();
        if (workerRegionState.containsRegisteredRegion(workerRegionActorRef)) {
            log.warning("Cluster Master >>> WORKER REGION {} ALREADY REGISTERED", workerRegionId);
            sender().tell(WORKER_REGION_ACK, self());
        } else {
            log.info("Cluster Master >>> WorkerRegion registered: WorkerRegionId : {}", workerRegionId);
            persist(new WorkerRegionEvent.Registered(workerRegionActorRef), this::handleRegisterWorkerRegionEvent);
        }
    }

    private void handleRegisterWorkerRegionEvent(WorkerRegionEvent.Registered registered) {
        workerRegionState.updateState(registered);
        ActorRef workerRegion = registered.getWorkerRegion();
        // Watch workerRegion in case it becomes unavailable (terminated message will be received)
        getContext().watch(workerRegion);
        sender().tell(WORKER_REGION_ACK, self());
        if (sensorDataState.hasPendingSensorData()) {
            sendDataToWorkers();
        }
    }

    private void handleDataProcessed(SensorDataEvent.DataProcessed dataProcessed) {
        sensorDataState.updateState(dataProcessed);
        // Publish with flag sendOneMessageToEachGroup set to true so that only one subscriber receives the message
        mediator.tell(new DistributedPubSubMediator.Publish(RESULTS_TOPIC, dataProcessed.getResult(), true), self());
    }

    @Override
    public String persistenceId() {
        return "master-singleton";
    }

}
