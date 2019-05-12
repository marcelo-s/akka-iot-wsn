package actors;

import akka.actor.AbstractActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import akka.persistence.SnapshotOffer;
import messages.AppMessages;
import state.WorkerState;

public class StatefulWorker extends AbstractPersistentActor {
    LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private WorkerState state = new WorkerState();

    Cluster cluster = Cluster.get(getContext().system());

    //subscribe to cluster changes, MemberUp
    @Override
    public void preStart() {
        cluster.subscribe(self(), ClusterEvent.MemberUp.class);
    }

    //re-subscribe when restart
    @Override
    public void postStop() {
        cluster.unsubscribe(self());
    }

    @Override
    public AbstractActor.Receive createReceiveRecover() {
        return receiveBuilder()
                .match(AppMessages.Event.class, this::onRecoverEvent)
                .match(SnapshotOffer.class, this::onRecoverSnapshot)
                .match(RecoveryCompleted.class, this::onRecoveryCompleted)
                .build();
    }

    private void onRecoverEvent(AppMessages.Event evt) {
        log.info(String.format("RECOVER FROM PERSISTENT ACTOR: %s", self().path().name()));
        log.info(String.format("Recover EVENT: %s", evt.getJobMessage().getPayload()));
        state.update(evt);
    }

    private void onRecoverSnapshot(SnapshotOffer ss) {
        WorkerState snapshotState = (WorkerState) ss.snapshot();
        log.info(String.format("FROM WORKER >>>>>>>>>>>>> %s , %sRecover SNAPSHOT: %s", self().path().name(), snapshotState));
        this.state = snapshotState;
    }

    private void onRecoveryCompleted(RecoveryCompleted r) {
        log.info("**********RECOVERY COMPLETED**********");
    }

    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder()
                .match(AppMessages.Command.class, this::onCommandReceived)
                .match(String.class, s -> s.equals("snap"), this::onSnapshotReceived)
                .match(ClusterEvent.CurrentClusterState.class, this::ignoreMessage)
                .matchAny(this::onAnyMessage)
                .build();

    }

    private void ignoreMessage(ClusterEvent.CurrentClusterState currentClusterState) {
        // do nothing with current state
    }


    private void onAnyMessage(Object o) {
        log.error("RECEIVED UNKNOWN MESSAGE ON BAKCEND : " + o.getClass().getName());
    }

    private void onCommandReceived(AppMessages.Command command) {
        if (commandIsValid()) {
            AppMessages.Event event = new AppMessages.Event(command.getJobMessage());
            persist(event, this::eventHandler);

        } else {
            System.out.println("Command invalid: Not persisted");
        }
    }

    private boolean commandIsValid() {
        // Implement logic to decide if the command is valid and needs to be persisted
        return true;
    }

    private void onSnapshotReceived(String str) {
        saveSnapshot(this.state.copy());
    }

    private void eventHandler(AppMessages.Event event) {
        state.update(event);
        log.info(String.format("FROM WORKER >>>>>>>>>>>>> %s AND JOB: %s", self().path(), event.getJobMessage().getPayload()));
        log.info(String.format("SENDER IS >>>>>>>>>>>>> %s ", sender().path()));
        sender().tell(new AppMessages.ResultMessage(String.format("FROM FRONTEND >>>>>>>>>>>: %s %s ROUTEE: %s",
                sender().path(),
                event.getJobMessage().getPayload().toUpperCase(),
                self().path())),
                self());
    }

    @Override
    public String persistenceId() {
        return "stateful-actor >>>>>>" + self().path().name();
    }
}
