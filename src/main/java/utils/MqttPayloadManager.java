package utils;

import actors.iot.SensorData;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.alpakka.mqtt.MqttQoS;
import akka.util.ByteString;

import java.io.*;

public class MqttPayloadManager {

    public static MqttMessage createMqttMessage(SensorData sensorData, String topic) {
        ByteString payload = MqttPayloadManager.createPayload(sensorData);
        return MqttMessage.create(topic, payload)
                .withQos(MqttQoS.atLeastOnce())
                .withRetained(true);
    }

    public static ByteString createPayload(SensorData sensorData) {
        byte[] byteArray = null;
        ByteArrayOutputStream byteArrayOutputStream = null;
        ObjectOutputStream objectOutputStream = null;
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(sensorData);
            byteArray = byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                byteArrayOutputStream.close();
                objectOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ByteString.fromArray(byteArray);
    }

    public static SensorData getPayloadFromMqttMessage(MqttMessage mqttMessage) {
        ObjectInputStream objectInputStream = null;
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(mqttMessage.payload().toArray());
        try {
            objectInputStream = new ObjectInputStream(byteArrayInputStream);
            Object o = objectInputStream.readObject();
            if (o instanceof SensorData) {
                return (SensorData) o;
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                byteArrayInputStream.close();
                objectInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
