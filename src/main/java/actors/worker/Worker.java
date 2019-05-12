package actors.worker;

import actors.iot.SensorData;
import actors.master.MasterWorkerProtocol;
import akka.actor.*;
import akka.cluster.client.ClusterClient;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Deadline;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Worker extends AbstractActor {

    private static final int MASTER_RESULTS_TIMEOUT = 10;
    private static final WorkerProtocol.RetryTick RETRY_TICK = new WorkerProtocol.RetryTick();

    private final ActorRef clusterClient;
    private ActorRef workProcessorRouter;
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final String workerId = UUID.randomUUID().toString();
    private final Map<String, WorkerProtocol.ResultDeadline> resultsToMasterDeadlinesMap = new HashMap<>();
    private final Map<String, List<Integer>> arimaOrdersParams;

    public static Props props(ActorRef clusterClient, ActorRef workProcessorRouter) {
        return Props.create(Worker.class, clusterClient, workProcessorRouter);
    }

    public Worker(ActorRef clusterClient, ActorRef workProcessorRouter) {
        this.clusterClient = clusterClient;
        this.workProcessorRouter = workProcessorRouter;
        arimaOrdersParams = createArimaOrderParamsMap();
        // Set retry task for completed works sent to Master
        setRetrySendingResultsToMasterTask();
    }

    private Map<String, List<Integer>> createArimaOrderParamsMap() {
        HashMap<String, List<Integer>> arimaParams = new HashMap<>();
        // Create p, d ,q params for different ARIMA models
        arimaParams.put("ArimaOrderParams1", Arrays.asList(0, 0, 0));
        arimaParams.put("ArimaOrderParams2", Arrays.asList(1, 0, 0));
        arimaParams.put("ArimaOrderParams3", Arrays.asList(0, 0, 1));
        arimaParams.put("ArimaOrderParams4", Arrays.asList(2, 1, 1));
        arimaParams.put("ArimaOrderParams5", Arrays.asList(3, 2, 1));
        return arimaParams;
    }

    private void setRetrySendingResultsToMasterTask() {
        ActorSystem system = getContext().getSystem();
        final FiniteDuration initialDelay = Duration.create(2, TimeUnit.SECONDS);
        final FiniteDuration interval = Duration.create(10, TimeUnit.SECONDS);
        final ExecutionContext ec = system.dispatcher();
        system.scheduler().schedule(
                initialDelay,
                interval,
                () -> self().tell(RETRY_TICK, self()),
                ec);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(WorkerProtocol.RetryTick.class, this::handleRetryTick)
                .match(SensorData.class, this::handleSensorData)
                .match(WorkerProtocol.WorkProcessed.class, this::handleWorkProcessed)
                .match(MasterWorkerProtocol.ProcessingCompletedAck.class, this::handleProcessingCompletedAck)
                .match(Terminated.class, this::handleTerminated)
                .matchAny(this::handleAny)
                .build();
    }


    private void handleTerminated(Terminated terminated) {
        log.info("Child of Worker Terminated : {}", terminated.getActor().getClass());
    }

    private void handleRetryTick(WorkerProtocol.RetryTick retryTick) {
        resultsToMasterDeadlinesMap.forEach((workId, resultDeadline) -> {
                    Deadline deadline = resultDeadline.getDeadline();
                    if (deadline.isOverdue()) {
                        log.info("Worker >>> Timeout of Results - No ack from Master SensorData Id {}", workId);
                        log.info("Worker >>> Retrying...");
                        // Update deadline
                        sendProcessingResultToMaster(resultDeadline.getProcessingResult());
                        resultDeadline.setDeadline(Deadline.now().$plus(Duration.create(MASTER_RESULTS_TIMEOUT, TimeUnit.SECONDS)));
                    }
                }
        );
    }

    // Supervisor for all children of Worker (WorkAggregator)
    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(
                DeciderBuilder
                        .match(ActorInitializationException.class, e -> SupervisorStrategy.stop())
                        .match(DeathPactException.class, e -> SupervisorStrategy.stop())
                        .matchAny(this::handleAnySupervisor)
                        .build());
    }

    private SupervisorStrategy.Directive handleAnySupervisor(Throwable throwable) {
        return SupervisorStrategy.restart();
    }

    private void sendToMaster(Object message) {
        clusterClient.tell(new ClusterClient.SendToAll("/user/master/singleton", message), self());
    }

    private void handleAny(Object o) {
        log.warning("Worker >>> HANDLE ANY - Received unknown object of class : {}", o.getClass().getName());
    }

    private void handleSensorData(SensorData sensorData) {
        log.info("Worker >>> Received SensorData Id: {}", sensorData.getDataId());
        String dataId = sensorData.getDataId();
        sendReceivedAckToMaster(dataId);
        ActorRef workAggregator = getContext().actorOf(
                Props.create(WorkAggregator.class, arimaOrdersParams.size(), sensorData, self()));
        sendSensorDataForProcessing(sensorData, workAggregator);
    }

    private void sendSensorDataForProcessing(SensorData sensorData, ActorRef workAggregator) {
        log.info("Worker >>> Sending to routees SensorData Id: {}", sensorData.getDataId());
        arimaOrdersParams.forEach((name, params) -> {
            WorkerProtocol.SensorDataModelTask sensorDataModelTask =
                    new WorkerProtocol.SensorDataModelTask(sensorData, params);
            workProcessorRouter.tell(sensorDataModelTask, workAggregator);
        });
    }

    private void handleWorkProcessed(WorkerProtocol.WorkProcessed workProcessed) {
        resultsToMasterDeadlinesMap.remove(workProcessed.getResult().getSensorDataId());
        ProcessingResult result = workProcessed.getResult();
        log.info("Worker >>> Processed work: {}-{} | SensorData Id {}", result.getSensorType(),
                result.getSensorId(), result.getSensorDataId());
        resultsToMasterDeadlinesMap.put(
                workProcessed.getResult().getSensorDataId(),
                new WorkerProtocol.ResultDeadline(result, Deadline.now().$plus(Duration.create(10, TimeUnit.SECONDS))));
        sendProcessingResultToMaster(result);
    }

    private void sendReceivedAckToMaster(String dataId) {
        sendToMaster(new MasterWorkerProtocol.SensorDataReceived(dataId));
    }

    private void sendProcessingResultToMaster(ProcessingResult result) {
        log.info("Worker >>> Sending work result to master, workId: " + result.getSensorDataId());
        sendToMaster(new MasterWorkerProtocol.ProcessingCompleted(workerId, result.getSensorDataId(), result));
    }

    private void handleProcessingCompletedAck(MasterWorkerProtocol.ProcessingCompletedAck processingCompletedAck) {
        resultsToMasterDeadlinesMap.remove(processingCompletedAck.getSensorDataId());
        log.info("Worker >>> Acknowledge results reception by Master of SensorData : {}", processingCompletedAck.getSensorDataId());
    }
}
