package actors.iot;

import akka.actor.ActorRef;

import java.io.Serializable;

public class SensorData implements Serializable {

    public static final long serialVersionUID = 2L;
    private final String dataId;
    private final Sensor.SensorType sensorType;
    private final String sensorId;
    private final double[] dataReadings;
    private final Sensor.OperationState operationState;
    private ActorRef iotManager;

    private SensorData(String dataId, Sensor.SensorType sensorType, String sensorId, double[] dataReadings, Sensor.OperationState operationState) {

        this.dataId = dataId;
        this.sensorType = sensorType;
        this.sensorId = sensorId;
        this.dataReadings = dataReadings;
        this.operationState = operationState;
    }

    public String getDataId() {
        return dataId;
    }

    public Sensor.SensorType getSensorType() {
        return sensorType;
    }

    public String getSensorId() {
        return sensorId;
    }

    public Sensor.OperationState getOperationState() {
        return operationState;
    }

    public double[] getDataReadings() {
        return dataReadings;
    }

    public ActorRef getIotManager() {
        return iotManager;
    }

    void setIotManager(ActorRef iotManager) {
        this.iotManager = iotManager;
    }

    static class SensorDataBuilder implements Serializable {
        private String dataId;
        private Sensor.SensorType sensorType;
        private String sensorId;
        private double[] dataReadings;
        private Sensor.OperationState operationState;
        private ActorRef iotManager;

        public SensorDataBuilder() {
        }

        public SensorDataBuilder setDataId(String dataId) {
            this.dataId = dataId;
            return this;
        }

        public SensorDataBuilder setSensorType(Sensor.SensorType sensorType) {
            this.sensorType = sensorType;
            return this;
        }

        public SensorDataBuilder setSensorId(String sensorId) {
            this.sensorId = sensorId;
            return this;
        }

        public SensorDataBuilder setDataReadings(double[] dataReadings) {
            this.dataReadings = dataReadings;
            return this;
        }

        public SensorDataBuilder setOperationState(Sensor.OperationState operationState) {
            this.operationState = operationState;
            return this;
        }

        public SensorDataBuilder setIotManager(ActorRef iotManager) {
            this.iotManager = iotManager;
            return this;
        }

        public SensorData build() {
            return new SensorData(
                    dataId,
                    sensorType,
                    sensorId,
                    dataReadings,
                    operationState
            );
        }
    }
}
