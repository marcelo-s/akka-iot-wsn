package actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.SupervisorStrategy;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import messages.AppMessages.JobMessage;
import messages.AppMessages.ResultMessage;

public class Backend extends AbstractLoggingActor {

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
    public SupervisorStrategy supervisorStrategy() {
        return super.supervisorStrategy();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(JobMessage.class, this::onJobReceived)
                .build();
    }

    private void onJobReceived(JobMessage job) {
        log().info(String.format("FROM BACKEND >>>>>>>>>>>>> %s AND JOB: %s", self().path().toString(), job.getPayload()));
        sender().tell(new ResultMessage(String.format("FROM FRONTEND >>>>>>>>>>>: %s %s ROUTEE: %s", sender().path(), job.getPayload().toUpperCase(), self().path())), self());
    }
}

