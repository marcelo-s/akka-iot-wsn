package actors.worker;

import actors.iot.SensorData;
import akka.cluster.sharding.ShardRegion;

public class WorkRegionMessageExtractor extends ShardRegion.HashCodeMessageExtractor {

    WorkRegionMessageExtractor(int maxNumberOfShards) {
        super(maxNumberOfShards);
    }

    @Override
    public String entityId(Object message) {
        if (message instanceof SensorData) {
            SensorData sensorData = (SensorData) message;
            return sensorData.getSensorId();
        } else {
            return null;
        }
    }

    // ShardId is done by modulo hashing operation defined in the HashCodeMessageExtractor abstract class
    // The message is passed as it is, which is an instance of the SensorData class
}
