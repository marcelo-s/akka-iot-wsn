package actors.master;

import actors.worker.ProcessingResult;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class ResultProcessor extends AbstractActor {

    private static final String TOPIC_GROUP = "group-processor";
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final ActorRef mediator;

    public static Props props() {
        return Props.create(ResultProcessor.class);
    }

    public ResultProcessor() {
        mediator = DistributedPubSub.get(getContext().system()).mediator();
        mediator.tell(new DistributedPubSubMediator.Subscribe(Master.RESULTS_TOPIC, TOPIC_GROUP,  getSelf()), getSelf());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(DistributedPubSubMediator.SubscribeAck.class, this::handleSubscriptionAck)
                .match(ProcessingResult.class, this::handleWorkResult)
                .build();
    }

    private void handleSubscriptionAck(DistributedPubSubMediator.SubscribeAck subscribeAck) {
        log.info("Result Processor >>> Subscribed to Topic : {}", Master.RESULTS_TOPIC);

    }

    private void handleWorkResult(ProcessingResult processingResult) {
        log.info("Result Processor >>> Received sensor processing result of SensorData Id: {}", processingResult.getSensorDataId());
        log.info("Result Processor >>> Sending processing result back to IoT Manager");
        processingResult.getIotManager().tell(processingResult, self());
    }
}
