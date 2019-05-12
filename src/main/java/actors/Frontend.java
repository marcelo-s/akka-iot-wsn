package actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion;
import akka.japi.Option;
import messages.AppMessages;

import static messages.AppMessages.FailedMessage;
import static messages.AppMessages.JobMessage;

public class Frontend extends AbstractLoggingActor {

    boolean hasBackend;
    Cluster cluster = Cluster.get(getContext().system());
    private final ActorRef deviceRegion;

    public static ShardRegion.MessageExtractor messageExtractor = new ShardRegion.MessageExtractor() {
        @Override
        public String entityId(Object message) {
            if (message instanceof AppMessages.Command)
                return String.valueOf(((AppMessages.Command) message).getJobMessage().getCounter());
            else {
                return null;
            }
        }

        @Override
        public Object entityMessage(Object message) {
            if (message instanceof AppMessages.Command)
                return message;
            else {
                return null;
            }
        }

        @Override
        public String shardId(Object message) {
            int numberOfShards = 10;
            if (message instanceof AppMessages.Command) {
                int id = ((AppMessages.Command) message).getJobMessage().getCounter();
                return String.valueOf(id % numberOfShards);
                // Needed if you want to use 'remember entities':
                // } else if (message instanceof ShardRegion.StartEntity) {
                //   long id = ((ShardRegion.StartEntity) message).id;
                //   return String.valueOf(id % numberOfShards)
            } else {
                return null;
            }
        }
    };


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

    public Frontend() {
        ActorSystem system = getContext().getSystem();
        Option<String> roleOption = Option.none();
        ClusterShardingSettings settings = ClusterShardingSettings.create(system);
        deviceRegion = ClusterSharding.get(system)
                .start(
                        "Counter",
                        Props.create(StatefulWorker.class),
                        settings,
                        messageExtractor);

//        getContext().getSystem().scheduler().schedule(
//                Duration.create(10, TimeUnit.SECONDS),
//                Duration.create(1, TimeUnit.SECONDS),
//                getSelf(),
//                UpdateDevice.INSTANCE,
//                system.dispatcher(),
//                null
//        );
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(JobMessage.class, job -> !hasBackend, this::onJobReceivedNoBackend)
                .match(JobMessage.class, this::onJobReceived)
                .match(AppMessages.ResultMessage.class, this::onResultReceived)
                .match(ClusterEvent.MemberUp.class, this::onMemberUp)
                .build();
    }

    private void onResultReceived(AppMessages.ResultMessage resultMessage) {
        log().info("RECEIVED RESULTE: " + resultMessage);
    }

    private void onJobReceivedNoBackend(JobMessage job) {
        sender().tell(new FailedMessage("Service unavailable, try again later", job), sender());
    }

    private void onJobReceived(JobMessage job) {
//        log().info("RECEIVED :" + job.getCounter());
        AppMessages.Command command = new AppMessages.Command(job);
        deviceRegion.tell(command, getSelf());
//        String workerIP = ConfigFactory.load().getString("clustering.seed1-ip");
//        String seedPort = ConfigFactory.load().getString("clustering.seed1-port");
//        String address = String.format("akka.tcp://%s@" + workerIP + ":" + seedPort, Main.CLUSTER_SYSTEM_NAME);
//        getContext().actorSelection(address + "/user/router1").forward(command, getContext());
    }

    private void onMemberUp(ClusterEvent.MemberUp memberUp) {
        Member member = memberUp.member();
        if (member.hasRole("backend")) {
            hasBackend = true;
        }
    }
}
