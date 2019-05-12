package actors.master;

import actors.iot.SensorData;
import actors.worker.ProcessingResult;

import java.io.Serializable;
import java.util.*;

public class SensorDataQueue {

    private Queue<SensorData> pendingSensorData = new LinkedList<>();
    private Map<String, SensorData> inProgressSensorDataMap = new HashMap<>();
    private Map<String, SensorData> sensorDataReceivedMap = new HashMap<>();
    private Set<String> acceptedSensorDataIds = new HashSet<>();
    private Set<String> completedSensorDataIds = new HashSet<>();

    public Boolean hasSensorData() {
        return !pendingSensorData.isEmpty();
    }

    public SensorData nextSensorData() {
        return pendingSensorData.peek();
    }

    public Boolean wasReceived(String sensorDataId) {
        return acceptedSensorDataIds.contains(sensorDataId);
    }

    public Boolean isInProgress(String sensorDataId) {
        return inProgressSensorDataMap.containsKey(sensorDataId);
    }

    public Boolean isCompleted(String sensorDataId) {
        return completedSensorDataIds.contains(sensorDataId);
    }

    public SensorData updated(SensorDataEvent event) {
        if (event instanceof DataAccepted) {
            DataAccepted dataAccepted = (DataAccepted) event;
            // Store all received data for further processing or querying
            sensorDataReceivedMap.put(dataAccepted.getSensorData().getSensorId(), dataAccepted.getSensorData());
            pendingSensorData.add(dataAccepted.getSensorData());
            acceptedSensorDataIds.add(dataAccepted.getSensorData().getDataId());
            return dataAccepted.getSensorData();
        } else if (event instanceof DataSentForProcess) {
            DataSentForProcess dataSentForProcess = (DataSentForProcess) event;
            SensorData sensorDataToStart = pendingSensorData.poll();
            assert (sensorDataToStart.getDataId().equals(dataSentForProcess.getSensorDataId()));
            inProgressSensorDataMap.put(sensorDataToStart.getDataId(), sensorDataToStart);
            return sensorDataToStart;
        } else if (event instanceof DataProcessed) {
            DataProcessed dataProcessed = (DataProcessed) event;
            SensorData removedSensorData = inProgressSensorDataMap.remove(dataProcessed.getWorkId());
            completedSensorDataIds.add(dataProcessed.getWorkId());
            return removedSensorData;
        } else if (event instanceof DataTimedOut) {
            DataTimedOut dataTimedOut = (DataTimedOut) event;
            SensorData interruptedSensorData = inProgressSensorDataMap.remove(dataTimedOut.getWorkId());
            pendingSensorData.add(interruptedSensorData);
            return interruptedSensorData;
        }

        return null;
    }

    public interface SensorDataEvent {
    }

    public static class DataAccepted implements SensorDataEvent, Serializable {
        private SensorData sensorData;

        public DataAccepted(SensorData sensorData) {
            this.sensorData = sensorData;
        }

        public SensorData getSensorData() {
            return sensorData;
        }
    }

    public static class DataSentForProcess implements SensorDataEvent, Serializable {
        private String sensorDataId;

        public DataSentForProcess(String sensorDataId) {
            this.sensorDataId = sensorDataId;
        }

        public String getSensorDataId() {
            return sensorDataId;
        }
    }

    public static class DataProcessed implements SensorDataEvent, Serializable {
        private final String workId;
        private final ProcessingResult result;

        public DataProcessed(String workId, ProcessingResult result) {

            this.workId = workId;
            this.result = result;
        }

        public String getWorkId() {
            return workId;
        }

        public ProcessingResult getResult() {
            return result;
        }
    }

    public static class DataTimedOut implements SensorDataEvent, Serializable {
        private String workId;

        public DataTimedOut(String workId) {

            this.workId = workId;
        }

        public String getWorkId() {
            return workId;
        }
    }
}
