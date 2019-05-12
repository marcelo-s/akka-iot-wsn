package actors.iot;

import actors.worker.ProcessingResult;
import akka.Done;
import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.Scheduler;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.stream.ActorMaterializer;
import akka.stream.alpakka.mqtt.MqttConnectionSettings;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.alpakka.mqtt.MqttQoS;
import akka.stream.alpakka.mqtt.javadsl.MqttSink;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.github.signaflo.math.stats.distributions.Normal;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Duration;
import utils.MqttPayloadManager;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class Sensor extends AbstractActor {

    private static class Tick {
    }

    public enum OperationState {
        ON, OFF, SLEEP
    }

    public enum SensorType {
        TEMPERATURE, HUMIDITY, PRESSURE
    }

    private static final Tick TICK = new Tick();
    private MqttConnectionSettings connectionSettings;
    private ActorMaterializer materializer;
    private String mqttTopic;
    private Cancellable tickSchedule;
    private SensorType sensorType;
    private String sensorId;
    private ThreadLocalRandom random;
    private OperationState operationState;
    private double[] modelForecastValues;
    private int measureInterval;
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private Scheduler scheduler;

    public static Props props(SensorType sensorType, String sensorId) {
        return Props.create(Sensor.class, sensorType, sensorId);
    }

    public Sensor(SensorType sensorType, String sensorId) {
        this.sensorType = sensorType;
        this.sensorId = sensorId;
        scheduler = getContext().getSystem().scheduler();
        setupMqtt();
        random = ThreadLocalRandom.current();
    }

    @Override
    public void preStart() {
        setSensorState();

        tickSchedule = scheduleOnceMessage();
        log.info("Sensor -> {}-{} started", sensorType, sensorId);
    }

    private void setSensorState() {
        operationState = OperationState.ON;
        measureInterval = random.nextInt(2, 6);
    }

    @Override
    public void postStop() {
        log.info("Sensor -> {}-{} stopped.", sensorType, sensorId);
    }

    private void setupMqtt() {
        Config config = ConfigFactory.load("iotmanager");
        mqttTopic = config.getString("mqtt.topic");
        String brokerAddress = config.getString("mqtt.brokerAddress");
        connectionSettings =
                MqttConnectionSettings.create(
                        brokerAddress,
                        "iot-device-subscriber-client",
                        new MemoryPersistence()
                )
                        .withCleanSession(true);

        materializer = ActorMaterializer.create(getContext());
    }

    private String nextDataId() {
        return UUID.randomUUID().toString();
    }

    private Cancellable scheduleOnceMessage() {
        final ExecutionContext ec = getContext().getSystem().dispatcher();
        return scheduler.scheduleOnce(
                Duration.create(measureInterval, TimeUnit.SECONDS),
                self(),
                Sensor.TICK,
                ec,
                self());
    }

    @Override
    public Receive createReceive() {
        return working();
    }

    private Receive working() {
        return receiveBuilder()
                .match(Tick.class, this::handleTick)
                .match(ProcessingResult.class, this::handleWorkResult)
                .build();
    }

    private void handleTick(Tick tick) {
        String dataId = nextDataId();
        double[] dataReadings = generateDataReadings();
        SensorData sensorData = new SensorData.SensorDataBuilder()
                .setDataId(dataId)
                .setSensorType(sensorType)
                .setSensorId(sensorId)
                .setDataReadings(dataReadings)
                .setOperationState(operationState)
                .build();
        log.info("Sensor >>> {}-{} generated SensorData Id: {}", sensorType, sensorId, dataId);

        Stream.of(sensorData.getDataReadings()).forEach(System.out::println);
        log.info("Sensor >>> Publishing to MQTT Topic for SensorData Id: {}", dataId);

        MqttMessage mqttMessage = MqttPayloadManager.createMqttMessage(sensorData, mqttTopic);
        publish(mqttMessage);
        getContext().become(waitAccepted(sensorData));
    }

    private double[] generateDataReadings() {
        Normal normal = new Normal(25, 3);

        double[] values = new double[7];
        for (int i = 0; i < values.length; i++) {
            values[i] = normal.rand();
        }
        return values;
    }

    private void handleWorkResult(ProcessingResult result) {
        log.info("Sensor >>> Id : {} - Type: {} - Received processing result of SensorData Id {}.",
                result.getSensorId(),
                result.getSensorType(),
                result.getSensorDataId());

        log.info("Sensor >>> Updated with : Interval {} ; Operation State {} ; Model Forecast {}",
                result.getMeasureInterval(),
                result.getOperationState(),
                modelForecastToString(result.getModelForecastValues()));
        measureInterval = result.getMeasureInterval();
        operationState = result.getOperationState();
        modelForecastValues = result.getModelForecastValues();

        rescheduleTick();
    }

    private String modelForecastToString(double[] modelForecast) {
        StringBuilder res = new StringBuilder();
        for (double value : modelForecast) {
            res.append(value).append("/");
        }
        return res.toString();
    }

    private void rescheduleTick() {
        tickSchedule.cancel();
        tickSchedule = scheduleOnceMessage();
    }

    private void publish(MqttMessage mqttMessage) {
        Sink<MqttMessage, CompletionStage<Done>> mqttSink =
                MqttSink.create(connectionSettings.withClientId(connectionSettings.clientId() + sensorId), MqttQoS.atMostOnce());
        Source.from(Collections.singletonList(mqttMessage)).runWith(mqttSink, materializer);
    }


    private Receive waitAccepted(SensorData sensorData) {
        return receiveBuilder()
                .match(IoTProtocol.SensorDataAccepted.class, (sensorDataAccepted) -> this.handleDataAccepted(sensorData))
                .match(IoTProtocol.SensorDataNotAccepted.class, (nok) -> this.handleDataNotAccepted(sensorData))
                .matchAny(this::handleAny)
                .build();

    }

    private void handleAny(Object o) {
        log.warning("Sensor >>> HANDLE ANY - Received unknown object of class : {}", o.getClass().getName());
    }

    private void handleDataAccepted(SensorData sensorData) {
        log.info("Sensor >>> Acknowledge from IoTManager received for SensorData Id {}", sensorData.getDataId());
        getContext().unbecome();
        tickSchedule = scheduleOnceMessage();
    }

    private void handleDataNotAccepted(SensorData sensorData) {
        log.warning("Sensor >>> No acknowledgement for SensorData Id {} >>> Retrying ... ", sensorData.getDataId());
        MqttMessage mqttMessage = MqttPayloadManager.createMqttMessage(sensorData, mqttTopic);
        publish(mqttMessage);
    }
}
