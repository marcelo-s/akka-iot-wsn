package actors.iot;

import actors.master.MasterIoTProtocol;
import actors.worker.ProcessingResult;
import akka.Done;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.client.ClusterClient;
import akka.dispatch.Mapper;
import akka.dispatch.Recover;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.alpakka.mqtt.MqttConnectionSettings;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.alpakka.mqtt.MqttQoS;
import akka.stream.alpakka.mqtt.MqttSubscriptions;
import akka.stream.alpakka.mqtt.javadsl.MqttSource;
import akka.stream.javadsl.Source;
import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import utils.MqttPayloadManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class IoTManager extends AbstractActor {

    private static final IoTProtocol.SensorDataAccepted SENSOR_DATA_ACCEPTED = new IoTProtocol.SensorDataAccepted();
    private static final IoTProtocol.SensorDataNotAccepted SENSOR_DATA_NOT_ACCEPTED = new IoTProtocol.SensorDataNotAccepted();
    private final String mqttTopic;
    private final String brokerAddress;
    private final ActorRef clusterClient;
    private final int numOfSensors;
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final ThreadLocalRandom random;
    private final Map<String, ActorRef> sensorIdToActorMap = new HashMap<>();
    private final Map<ActorRef, String> sensorActorToIdMap = new HashMap<>();


    public static Props props(ActorRef clusterClient, int numOfDevices) {
        return Props.create(IoTManager.class, clusterClient, numOfDevices);
    }

    public IoTManager(ActorRef clusterClient, int numOfSensors) {
        this.clusterClient = clusterClient;
        this.numOfSensors = numOfSensors;
        random = ThreadLocalRandom.current();
        Config config = ConfigFactory.load("iotmanager");
        mqttTopic = config.getString("mqtt.topic");
        brokerAddress = config.getString("mqtt.brokerAddress");
    }

    @Override
    public void preStart() {
        createSensors();
        subscribeToDevicesTopic();
    }

    private void createSensors() {
        Sensor.SensorType[] sensorTypes = Sensor.SensorType.values();
        IntStream.range(1, numOfSensors + 1).forEach(n -> {
                    Sensor.SensorType sensorType = sensorTypes[random.nextInt(0, sensorTypes.length)];
                    String sensorId = UUID.randomUUID().toString();
                    ActorRef sensorActor = getContext().actorOf(
                            Sensor.props(sensorType, sensorId),
                            String.format("%s-%s", sensorType, sensorId));
                    getContext().watch(sensorActor);
                    sensorActorToIdMap.put(sensorActor, sensorId);
                    sensorIdToActorMap.put(sensorId, sensorActor);
                }
        );
        log.info("IoT Manager >>> Created {} sensors", numOfSensors);
    }

    @Override
    public void postStop() {
        log.info("IoT Manager >>> Stopped");
    }

    private void subscribeToDevicesTopic() {
        String clientId = UUID.randomUUID().toString();

        MqttConnectionSettings connectionSettings = MqttConnectionSettings.create(
                brokerAddress,
                "iot-manager-client-" + clientId,
                new MemoryPersistence()
        ).withCleanSession(true);
        ActorMaterializer materializer = ActorMaterializer.create(getContext());

        MqttSubscriptions subscriptions =
                MqttSubscriptions.create(mqttTopic, MqttQoS.atMostOnce());

        int bufferSize = 15;
        Source<MqttMessage, CompletionStage<Done>> mqttSource =
                MqttSource.atMostOnce(
                        connectionSettings, subscriptions, bufferSize);

        mqttSource
                .map(MqttPayloadManager::getPayloadFromMqttMessage)
                .runForeach(this::handleMqttMessageReceived, materializer)
                .thenAccept(d -> log.info("IoTManager >>> Subscription is done"));
    }

    private void handleMqttMessageReceived(SensorData sensorData) {
        log.info("IoTManager >>> Received MQTT message: Sensor type : {} ; SensorId : {}",
                sensorData.getSensorType(),
                sensorData.getSensorId()
        );
        log.info("IoTManager >>> Sending sensorData : {} to cluster master", sensorData.getDataId());

        ActorRef actorToPipe = sensorIdToActorMap.get(sensorData.getSensorId());
        if (actorToPipe == null) {
            // MQTT broker can have retained messages from other systems/sensors
            log.warning("IoT Manager >>> Sensor not registered, IGNORING : Sensor {}-{}", sensorData.getSensorType(), sensorData.getSensorId());
        } else {
            // Set the ActorRef to this IoTManager, for the master to return the result of processing
            sensorData.setIotManager(self());
            MasterIoTProtocol.SensorDataForProcess sensorDataForProcess = new MasterIoTProtocol.SensorDataForProcess(sensorData);
            ClusterClient.SendToAll message = new ClusterClient.SendToAll("/user/master/singleton", sensorDataForProcess);
            Timeout timeout = new Timeout(5, TimeUnit.SECONDS);
            final ExecutionContext ec = getContext().system().dispatcher();

            // Use the ask pattern as a request-response for acknowledging the master's reception of the data
            Future<Object> sensorDataToMasterFuture = Patterns.ask(clusterClient, message, timeout)
                    .map(new Mapper<Object, Object>() {
                        @Override
                        public Object apply(Object msg) {
                            if (msg instanceof MasterIoTProtocol.SensorDataReceived) {
                                log.info("IoT Manager >>> Received SensorData ack from Master, sensorDataId : {}", ((MasterIoTProtocol.SensorDataReceived) msg).getSensorDataId());
                                return SENSOR_DATA_ACCEPTED;
                            } else {
                                log.warning("IoT Manager >>> No SensorData ack received from master, received object of class : " + msg.getClass().getName());
                                return SENSOR_DATA_NOT_ACCEPTED;
                            }
                        }
                    }, ec).recover(new Recover<Object>() {
                        @Override
                        public Object recover(Throwable failure) {
                            log.warning("IoT Manager >>> Exception waiting for ack from Master : " + failure.getClass().getCanonicalName());
                            return SENSOR_DATA_NOT_ACCEPTED;
                        }
                    }, ec);

            Patterns.pipe(sensorDataToMasterFuture, getContext().getSystem().dispatcher()).to(actorToPipe);
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ProcessingResult.class, this::handleProcessingResult)
                .match(Terminated.class, this::HandleTerminated)
                .matchAny(this::handleAny)
                .build();
    }

    private void handleProcessingResult(ProcessingResult processingResult) {
        if (sensorIdToActorMap.containsKey(processingResult.getSensorId())) {
            ActorRef sensorActor = sensorIdToActorMap.get(processingResult.getSensorId());
            sensorActor.forward(processingResult, getContext());
            log.info("IoT Manager >>> SensorData result forwarded to {}-{} ", processingResult.getSensorType(), processingResult.getSensorId());
        } else {
            log.warning("IoT Manager >>> SensorId: {} not registered!", processingResult.getSensorType(), processingResult.getSensorId());
        }
    }

    private void HandleTerminated(Terminated terminated) {
        ActorRef sensorActor = terminated.getActor();
        String sensorId = sensorActorToIdMap.get(sensorActor);
        log.warning("IoT Manager >>> Sensor actor terminated, Sensor Id {} will be removed.", sensorId);
        sensorActorToIdMap.remove(sensorActor);
        sensorIdToActorMap.remove(sensorId);
    }

    private void handleAny(Object o) {
        log.warning("IoT Manager >>> HANDLE ANY - Received unknown object of class : {}", o.getClass().getName());
    }
}
