package actors.master;

import akka.actor.ActorRef;

import java.io.Serializable;

public interface WorkerRegionEvent {
    class Registered implements WorkerRegionEvent, Serializable {
        private ActorRef workerRegion;

        public Registered(ActorRef workerRegion){
            this.workerRegion = workerRegion;
        }

        public ActorRef getWorkerRegion() {
            return workerRegion;
        }
    }
    class Unregistered implements WorkerRegionEvent, Serializable {
        private ActorRef workerRegion;

        public Unregistered(ActorRef workerRegion){
            this.workerRegion = workerRegion;
        }

        public ActorRef getWorkerRegion() {
            return workerRegion;
        }
    }
}
