package actors.master;

import actors.iot.SensorData;
import scala.concurrent.duration.Deadline;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class SensorDataState {

    private Queue<SensorData> pendingSensorData = new LinkedList<>();
    private Map<String, SensorData> inProgressSensorDataMap = new HashMap<>();
    private Map<String, SensorData> sensorDataReceivedMap = new HashMap<>();
    private Set<String> acceptedSensorDataIds = new HashSet<>();
    private Set<String> completedSensorDataIds = new HashSet<>();
    private Map<String, Deadline> workerReceptionTimeouts = new HashMap<>();

    public SensorData updateState(SensorDataEvent event) {
        if (event instanceof SensorDataEvent.DataAccepted) {
            SensorDataEvent.DataAccepted dataAccepted = (SensorDataEvent.DataAccepted) event;
            // Store all received data for further processing or querying
            sensorDataReceivedMap.put(dataAccepted.getSensorData().getSensorId(), dataAccepted.getSensorData());
            pendingSensorData.add(dataAccepted.getSensorData());
            acceptedSensorDataIds.add(dataAccepted.getSensorData().getDataId());
            return dataAccepted.getSensorData();
        } else if (event instanceof SensorDataEvent.DataSentForProcess) {
            SensorDataEvent.DataSentForProcess dataSentForProcess = (SensorDataEvent.DataSentForProcess) event;
            SensorData sensorDataToStart = pendingSensorData.poll();
            assert (sensorDataToStart.getDataId().equals(dataSentForProcess.getSensorDataId()));
            System.out.println("SensorDataToStart: " + sensorDataToStart);
            inProgressSensorDataMap.put(sensorDataToStart.getDataId(), sensorDataToStart);
            FiniteDuration workTimeout = Duration.create(10, TimeUnit.SECONDS);
            workerReceptionTimeouts.put(sensorDataToStart.getDataId(), Deadline.now().$plus(workTimeout));
            return sensorDataToStart;
        } else if (event instanceof SensorDataEvent.DataProcessed) {
            SensorDataEvent.DataProcessed dataProcessed = (SensorDataEvent.DataProcessed) event;
            SensorData removedSensorData = inProgressSensorDataMap.remove(dataProcessed.getDataId());
            completedSensorDataIds.add(dataProcessed.getDataId());
            return removedSensorData;
        } else if (event instanceof SensorDataEvent.DataReceivedByWorker) {
            workerReceptionTimeouts.remove(((SensorDataEvent.DataReceivedByWorker) event).getDataId());
        } else if (event instanceof SensorDataEvent.DataTimeoutExpired) {
            SensorDataEvent.DataTimeoutExpired dataTimeoutExpired = (SensorDataEvent.DataTimeoutExpired) event;
            SensorData interruptedSensorData = inProgressSensorDataMap.remove(dataTimeoutExpired.getDataId());
            pendingSensorData.add(interruptedSensorData);
            return interruptedSensorData;
        }

        return null;
    }

    public Boolean hasPendingSensorData() {
        return !pendingSensorData.isEmpty();
    }

    public SensorData nextSensorData() {
        return pendingSensorData.peek();
    }

    public Boolean wasReceived(String sensorDataId) {
        return acceptedSensorDataIds.contains(sensorDataId);
    }

    public int getPendingSensorData(){
        return pendingSensorData.size();
    }

    public Boolean isInProgress(String sensorDataId) {
        return inProgressSensorDataMap.containsKey(sensorDataId);
    }

    public Boolean isCompleted(String sensorDataId) {
        return completedSensorDataIds.contains(sensorDataId);
    }

    public Map<String, Deadline> getWorkerReceptionTimeouts() {
        return workerReceptionTimeouts;
    }
}
