package actors.master;

import akka.actor.ActorRef;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class WorkerRegionState {

    private Set<ActorRef> workerRegionSet = new HashSet<>();

    void updateState(WorkerRegionEvent event) {
        if (event instanceof WorkerRegionEvent.Registered) {
            ActorRef registeredRegion = ((WorkerRegionEvent.Registered) event).getWorkerRegion();
            workerRegionSet.add(registeredRegion);
        } else if (event instanceof WorkerRegionEvent.Unregistered) {
            ActorRef unregisteredRegion = ((WorkerRegionEvent.Unregistered) event).getWorkerRegion();
            workerRegionSet.remove(unregisteredRegion);
        } else {
            throw new IllegalArgumentException();
        }
    }

    public boolean containsRegisteredRegion(ActorRef workerRegion) {
        return workerRegionSet.contains(workerRegion);
    }

    // Get first region. All regions use the shard coordinator to pass messages to the appropriate shard
    public Optional<ActorRef> getAvailableWorkerRegion() {
        return workerRegionSet.stream().findFirst();
    }

    public int getWorkerRegionsCount() {
        return workerRegionSet.size();
    }

    public Set<ActorRef> getWorkerRegions() {
        return workerRegionSet;
    }
}
