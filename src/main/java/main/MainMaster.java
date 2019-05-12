package main;

import actors.ClusterListener;
import actors.master.Master;
import actors.master.ResultProcessor;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

public class MainMaster {

    public static void main(String[] args) {

        Config config = ConfigFactory.load();
        String clusterName = config.getString("clustering.cluster.name");
        String role = config.getString("clustering.role");

        ActorSystem system = ActorSystem.create(clusterName, config);

        system.actorOf(Props.create(ClusterListener.class), "cluster-listener-master");
        FiniteDuration receiveAckTimeout = Duration.create(10, TimeUnit.SECONDS);
        ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(system).withRole(role);

        // Create singleton master actor
        system.actorOf(
                ClusterSingletonManager.props(
                        Master.props(receiveAckTimeout),
                        PoisonPill.getInstance(),
                        settings),
                "master");
        // Create result processor for the master
        system.actorOf(ResultProcessor.props(), "result-processor");
    }

}
