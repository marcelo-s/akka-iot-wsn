package actors.worker;

import actors.iot.SensorData;
import scala.concurrent.duration.Deadline;

import java.io.Serializable;
import java.util.List;

public interface WorkerProtocol {
    class ResultDeadline implements WorkerProtocol, Serializable {
        private final ProcessingResult processingResult;
        private Deadline deadline;

        ResultDeadline(ProcessingResult processingResult, Deadline deadline) {
            this.processingResult = processingResult;
            this.deadline = deadline;
        }

        ProcessingResult getProcessingResult() {
            return processingResult;
        }

        Deadline getDeadline() {
            return deadline;
        }

        void setDeadline(Deadline deadline) {
            this.deadline = deadline;
        }
    }

    class RetryTick implements WorkerProtocol, Serializable {
    }

    class WorkProcessed implements WorkerProtocol, Serializable {
        private ProcessingResult result;

        WorkProcessed(ProcessingResult result) {
            this.result = result;
        }

        ProcessingResult getResult() {
            return result;
        }
    }

    class SensorDataModelTask implements WorkerProtocol, Serializable {

        private final SensorData sensorData;
        private final List<Integer> arimaOrderParams;

        public SensorDataModelTask(SensorData sensorData, List<Integer> arimaOrderParams) {
            this.sensorData = sensorData;
            this.arimaOrderParams = arimaOrderParams;
        }

        public SensorData getSensorData() {
            return sensorData;
        }

        public List<Integer> getArimaOrderParams() {
            return arimaOrderParams;
        }
    }

    class SensorDataModelProcessed implements Serializable {
        private List<Integer> arimaParams;
        private double[] predictions;
        private double aic;

        public SensorDataModelProcessed(List<Integer> arimaParams, double[] predictions, double aic) {
            this.arimaParams = arimaParams;
            this.predictions = predictions;
            this.aic = aic;
        }

        public double[] getPredictions() {
            return predictions;
        }

        public double getAic() {
            return aic;
        }

        public List<Integer> getArimaParams() {
            return arimaParams;
        }
    }

}
