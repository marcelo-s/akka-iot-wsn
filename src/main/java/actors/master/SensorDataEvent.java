package actors.master;

import actors.iot.SensorData;
import actors.worker.ProcessingResult;

import java.io.Serializable;

public interface SensorDataEvent {
    class DataAccepted implements SensorDataEvent, Serializable {
        private SensorData sensorData;

        DataAccepted(SensorData sensorData) {
            this.sensorData = sensorData;
        }

        SensorData getSensorData() {
            return sensorData;
        }
    }

    class DataSentForProcess implements SensorDataEvent, Serializable {
        private String sensorDataId;

        DataSentForProcess(String sensorDataId) {
            this.sensorDataId = sensorDataId;
        }

        String getSensorDataId() {
            return sensorDataId;
        }
    }

    class DataProcessed implements SensorDataEvent, Serializable {
        private final String dataId;
        private final ProcessingResult result;

        DataProcessed(String dataId, ProcessingResult result) {

            this.dataId = dataId;
            this.result = result;
        }

        String getDataId() {
            return dataId;
        }

        ProcessingResult getResult() {
            return result;
        }
    }

    class DataReceivedByWorker implements SensorDataEvent, Serializable {
        private String dataId;

        DataReceivedByWorker(String dataId) {
            this.dataId = dataId;
        }

        String getDataId() {
            return dataId;
        }
    }

    class DataTimeoutExpired implements SensorDataEvent, Serializable {
        private String dataId;

        DataTimeoutExpired(String dataId) {
            this.dataId = dataId;
        }

        String getDataId() {
            return dataId;
        }
    }
}

