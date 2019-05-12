package actors.worker;

import actors.iot.Sensor;
import actors.iot.SensorData;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class WorkAggregator extends AbstractActor {

    private List<WorkerProtocol.SensorDataModelProcessed> arimaModelsProcessed;
    private int numOfModelsToCompute;
    private final ActorRef workerRef;
    private SensorData sensorData;
    private ThreadLocalRandom random;
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props(int modelsToCompute, SensorData sensorData, ActorRef workerRef) {
        return Props.create(WorkAggregator.class, modelsToCompute, sensorData, workerRef);
    }

    public WorkAggregator(int numOfModelsToCompute, SensorData sensorData, ActorRef workerRef) {
        this.numOfModelsToCompute = numOfModelsToCompute;
        this.sensorData = sensorData;
        this.workerRef = workerRef;
        this.arimaModelsProcessed = new ArrayList<>();
        this.random = ThreadLocalRandom.current();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(WorkerProtocol.SensorDataModelProcessed.class, this::handleModelProcessed)
                .build();
    }

    private void handleModelProcessed(WorkerProtocol.SensorDataModelProcessed sensorDataModelProcessed) {
        log.info("Work Aggregator >>> SensorDataId : {}  >> Received model params: {}",
                sensorData.getDataId(),
                sensorDataModelProcessed.getArimaParams());
        numOfModelsToCompute -= 1;
        if (numOfModelsToCompute == 0) {
            arimaModelsProcessed.add(sensorDataModelProcessed);
            ProcessingResult processingResult = generateProcessingResult();
            log.info("Work Aggregator >>> SensorDataId : {}  > Finished, sending results back to Worker ", sensorData.getDataId());
            workerRef.tell(new WorkerProtocol.WorkProcessed(processingResult), self());
        } else {
            arimaModelsProcessed.add(sensorDataModelProcessed);
        }
    }

    private ProcessingResult generateProcessingResult() {
        Optional<WorkerProtocol.SensorDataModelProcessed> bestFitModelOptional = selectBestFitModel();
        if (bestFitModelOptional.isPresent()) {
            WorkerProtocol.SensorDataModelProcessed bestFitModel = bestFitModelOptional.get();
            double[] modelForecastValues = bestFitModel.getPredictions();
            Sensor.OperationState newOperationalState = computeOperationalState();
            int newMeasureInterval = computeMeasureInterval();
            return new ProcessingResult.ProcessingResultBuilder()
                    .setIotManager(sensorData.getIotManager())
                    .setSensorDataId(sensorData.getDataId())
                    .setSensorType(sensorData.getSensorType())
                    .setSensorId(sensorData.getSensorId())
                    .setMeasureInterval(newMeasureInterval)
                    .setModelForecastValues(modelForecastValues)
                    .setOperationState(newOperationalState)
                    .build();

        } else {
            log.warning("Worker Aggregator >> NO MODEL WAS SELECTED");
            throw new IllegalStateException("No model for Processing Result");
        }
    }

    private Optional<WorkerProtocol.SensorDataModelProcessed> selectBestFitModel() {
        return arimaModelsProcessed.stream().min((model1, model2) -> (int) (model1.getAic() - model2.getAic()));
    }

    private int computeMeasureInterval() {
        return random.nextInt(5, 10);
    }

    private Sensor.OperationState computeOperationalState() {
        Sensor.OperationState[] values = Sensor.OperationState.values();
        return values[random.nextInt(0, values.length)];
    }
}
