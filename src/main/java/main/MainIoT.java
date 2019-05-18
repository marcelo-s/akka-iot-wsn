package main;

import actors.listeners.ClusterListener;
import actors.iot.IoTManager;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.client.ClusterClient;
import akka.cluster.client.ClusterClientSettings;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;


public class MainIoT {


    public static void main(String[] args) {

        int numberOfDevices = Integer.valueOf(args[0]);

        final Config config = ConfigFactory.load("iotmanager");
        String systemName = config.getString("clustering.cluster.name");
        String port = config.getString("clustering.port");

        ActorSystem system = ActorSystem.create(systemName, config);

        system.actorOf(Props.create(ClusterListener.class), "cluster-listener-iot");

        final ActorRef clusterClient =
                system.actorOf(
                        ClusterClient.props(
                                ClusterClientSettings.create(system)),
                        "clusterClient");

        system.actorOf(IoTManager.props(clusterClient, numberOfDevices), "iot-manager");


    }


}
