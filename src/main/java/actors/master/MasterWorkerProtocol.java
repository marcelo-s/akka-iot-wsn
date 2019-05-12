package actors.master;

import actors.worker.ProcessingResult;
import akka.actor.ActorRef;

import java.io.Serializable;

public interface MasterWorkerProtocol {

    class RegisterWorkerRegion implements Serializable {
        private String workerRegionId;
        private ActorRef regionActorRef;

        public RegisterWorkerRegion(String workerRegionId, ActorRef regionActorRef) {

            this.workerRegionId = workerRegionId;
            this.regionActorRef = regionActorRef;
        }

        String getWorkerRegionId() {
            return workerRegionId;
        }

        ActorRef getRegionActorRef() {
            return regionActorRef;
        }
    }

    class WorkerRegionAck implements Serializable {
    }

    class RecoverWorkerRegion implements Serializable {

    }

    class RemoveUnrecoverableWorkerRegion implements Serializable {
        private ActorRef workerRegion;

        public RemoveUnrecoverableWorkerRegion(ActorRef workerRegion) {
            this.workerRegion = workerRegion;
        }

        public ActorRef getWorkerRegion() {
            return workerRegion;
        }
    }


    class ProcessingCompleted implements Serializable {
        private final String workerId;
        private final String sensorDataId;
        private final ProcessingResult result;

        public ProcessingCompleted(String workerId, String sensorDataId, ProcessingResult result) {
            this.workerId = workerId;
            this.sensorDataId = sensorDataId;
            this.result = result;
        }

        String getWorkerId() {
            return workerId;
        }

        String getSensorDataId() {
            return sensorDataId;
        }

        public ProcessingResult getResult() {
            return result;
        }
    }

    class ProcessingCompletedAck implements Serializable {
        private String sensorDataId;

        ProcessingCompletedAck(String sensorDataId) {
            this.sensorDataId = sensorDataId;
        }

        public String getSensorDataId() {
            return sensorDataId;
        }
    }

    class SensorDataReceived implements scala.Serializable {
        private String dataId;

        public SensorDataReceived(String dataId) {
            this.dataId = dataId;
        }

        public String getDataId() {
            return dataId;
        }
    }
}
