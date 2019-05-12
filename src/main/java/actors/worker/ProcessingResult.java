package actors.worker;

import actors.iot.Sensor;
import akka.actor.ActorRef;

import java.io.Serializable;

public class ProcessingResult implements Serializable {
    private final String sensorDataId;
    private final Sensor.SensorType sensorType;
    private final String sensorId;
    private final int measureInterval;
    private final Sensor.OperationState operationState;
    private final double[] modelForecastValues;
    private final ActorRef iotManager;

    private ProcessingResult(ActorRef iotManager, String sensorDataId, Sensor.SensorType sensorType, String sensorId, int measureInterval, double[] modelForecastValues, Sensor.OperationState operationState) {
        this.iotManager = iotManager;
        this.sensorDataId = sensorDataId;
        this.sensorType = sensorType;
        this.sensorId = sensorId;
        this.measureInterval = measureInterval;
        this.modelForecastValues = modelForecastValues;
        this.operationState = operationState;
    }

    public String getSensorDataId() {
        return sensorDataId;
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

    public int getMeasureInterval() {
        return measureInterval;
    }

    public ActorRef getIotManager() {
        return iotManager;
    }

    public double[] getModelForecastValues() {
        return modelForecastValues;
    }

    public static class ProcessingResultBuilder {
        private String sensorDataId;
        private Sensor.SensorType sensorType;
        private String sensorId;
        private int measureInterval;
        private Sensor.OperationState operationState;
        private double[] modelForecastValues;
        private ActorRef iotManager;

        ProcessingResultBuilder() {
        }

        ProcessingResultBuilder setSensorDataId(String sensorDataId) {
            this.sensorDataId = sensorDataId;
            return this;
        }

        ProcessingResultBuilder setSensorType(Sensor.SensorType sensorType) {
            this.sensorType = sensorType;
            return this;
        }

        ProcessingResultBuilder setSensorId(String sensorId) {
            this.sensorId = sensorId;
            return this;
        }

        ProcessingResultBuilder setMeasureInterval(int measureInterval) {
            this.measureInterval = measureInterval;
            return this;
        }

        ProcessingResultBuilder setOperationState(Sensor.OperationState operationState) {
            this.operationState = operationState;
            return this;
        }

        ProcessingResultBuilder setModelForecastValues(double[] modelForecastValues) {
            this.modelForecastValues = modelForecastValues;
            return this;
        }

        ProcessingResultBuilder setIotManager(ActorRef iotManager) {
            this.iotManager = iotManager;
            return this;
        }

        ProcessingResult build() {
            return new ProcessingResult(
                    iotManager,
                    sensorDataId,
                    sensorType,
                    sensorId,
                    measureInterval,
                    modelForecastValues,
                    operationState
            );
        }
    }
}
