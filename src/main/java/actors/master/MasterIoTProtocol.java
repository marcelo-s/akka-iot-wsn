package actors.master;

import actors.iot.SensorData;

import java.io.Serializable;

public interface MasterIoTProtocol {
    class SensorDataForProcess implements MasterIoTProtocol, Serializable {
        private SensorData sensorData;

        public SensorDataForProcess(SensorData sensorData){
            this.sensorData = sensorData;
        }

        public SensorData getSensorData() {
            return sensorData;
        }
    }
    class SensorDataReceived implements MasterIoTProtocol, Serializable {
        private String sensorDataId;

        SensorDataReceived(String sensorDataId) {
            this.sensorDataId = sensorDataId;
        }

        public String getSensorDataId() {
            return sensorDataId;
        }
    }
}
