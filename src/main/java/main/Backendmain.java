package main;

import actors.Backend;
import actors.ClusterListener;
import actors.Frontend;
import actors.StatefulWorker;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.japi.Option;
import akka.management.javadsl.AkkaManagement;
import akka.routing.FromConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import static utils.ConfigUtils.getConfig;

public class Backendmain {
    public static void main(String[] args) {

//        final Config config = getConfig("backend", null);
//
//        ActorSystem system = ActorSystem.create(Main.CLUSTER_SYSTEM_NAME, config);
//
//        system.actorOf(Props.create(ClusterListener.class));
//
//        createShardingRegion(system);

//        String seedPort = ConfigFactory.load().getString("clustering.port");
        // Start management system only on port 2560
//        if (seedPort.equals("2560")) {
//            startManagementSystem(system);
//        }

//        system.actorOf(Props.create(StatefulWorker.class), "router1");
    }

    private static void createShardingRegion(ActorSystem system) {
        Option<String> roleOption = Option.none();
        ClusterShardingSettings settings = ClusterShardingSettings.create(system);
        ActorRef deviceRegion = ClusterSharding.get(system)
                .start(
                        "Counter",
                        Props.create(StatefulWorker.class),
                        settings,
                        Frontend.messageExtractor);
    }

    private static void startManagementSystem(ActorSystem system) {
        AkkaManagement.get(system).start();
    }
}