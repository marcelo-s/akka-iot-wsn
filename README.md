# Akka (Java) IoT Wireless Sensor Network with Docker Swarm

This is a Java implementation of an IoT Scenario that consist of a Wireless Sensor Network that sends readings from
the sensors to create forecasting models using the ARIMA technique.

Yes, this is a Java application! Finally!

## Architecture components

The IoT scenario has the following componets:

* IoT Cluster: Cluster with the IoT sensors and an IoT manager. The manager is in charge of receiving all the data from 
the sensors and passing it to the Master cluster. Uses Mosquitto broker.
* Master Cluster: Cluster that acts as a main controller of the whole application. Among its main functions are to 
receive the data from the IoT cluster and send it to the Worker cluster for processing. Also, it is in charge of
 sending the results of the processing back to the IoT manager. This cluster maintains a record of all the events in a 
 distributed journal using the Cassandra Database. 
* Worker Cluster: Cluster in charge of data processing. This cluster distributes all the processing tasks among the 
nodes in the cluster. This cluster uses Akka Sharding to distribute the processing data, where the entities on the 
shard correspond to the sensors from the IoT cluster. The cluster also uses Akka's Cluster-aware routers to further distribute
the forecasting task among the nodes of the cluster.

### Prerequisites

* Have a docker setup running in swarm mode. This means that all nodes must have docker installed and running
in swarm mode.

     Please refer to: [Getting started with swarm mode](https://docs.docker.com/engine/swarm/swarm-tutorial/)

**IMPORTANT: All commands must be executed on the manager node of the swarm**

It is also possible to run this application without Docker. Another repository will be created for this local version.

### Docker images

Make sure the corresponding images match the different nodes hardware architectures.
You can build the images locally, using the provided Dockerfile, or use the images available on Docker Hub (default).


For this application, Raspberry Pi devices were used as Worker nodes, so specific images were needed for 
ARM architectures: See the Dockerfile.

However, you can play as much as you want. You can deploy the whole application in one Linux machine, although that 
would most likely kill your RAM. The Mosquitto broker can be deployed as well on a ARM machine, but not the Cassandra
service, as there is no Docker image (at the moment of committing this file) for ARM.

All the services can be deployed on different machines, just change the docker-compose.yml file in the 
"deploy > placement > constraints" value.


#### Docker swarm setup

In general follow these steps: [Create a swarm](https://docs.docker.com/engine/swarm/swarm-tutorial/create-swarm/)

However it could be as simple as this:
* Go to the manager to be node and execute the following command: 　
```
docker swarm init
```
This will create the swarm with the node as manager. It will also display the command to use to join 
a worker node. It should be something like the following:

```
docker swarm join \
    --token SWMTKN-1-49nj1cmql0jkz5s954yi3oex3nedyz0fb0xx14ie39trti4wxv-8vxv8rssmk743ojnwacrr2e7c \
    192.168.99.100:2377

``` 
The above command is just an example, use the appropriate token provided when the swarm was started.

Now go to the worker nodes and use that command to join the cluster.
 
Now back to the manager node, clone the project and go to the location of the project.

Once on the location of the cloned project, deploy the stack to the swarm:

```
docker stack deploy --compose-file docker-compose.yml akkaclusterswarm  
```

(The "akkaclusterswarm" is just the name of the stack and can be changed)

This will deploy all the services defined in the docker-compose.yml file.

The required images are located on the public Docker Hub, so it can be downloaded directly from any node.

The hostnames and ports of the nodes are being set as environment variables on the
docker-compose.yml file. Then on the application.conf these variables are obtained using 
the HOCON syntax and being replaced in the corresponding places.

To see the deployed stack, type the following command the manager node:
```
docker stack ls
```
This should show all the stacks, look for the one named "akkaclusterswarm" or whatever is the name of the stack.

To see the services deployed by the stack:
```
docker stack ps akkaclusterswarm 
```

This should list all the services that are listed on the docker-compose.yml file along with its state

###See logs/details of the state of a service

IMPORTANT: It can take a while to download the images, so the services will not start immediately.


Once the command for deploying is used, only a single line indicating that the service is deployed (for each service)
is shown, which is not useful at all.

To see the state of each service deployed use the previous command to see the services of the stack and then
look for the column ID of the service. With this information use this command:

```
docker inspect serviceid
```

Replacing "serviceid" by the ide of the corresponding service.

This will show full information about the service, look at the property "Status", which will show
useful information in case there is a problem starting the service such as: 

"No such image" or "No suitable nodes"

"No suitable nodes" happens when you try to deploy a service on a node that cannot host it. For example, 
deploying the application with an image for a x86 machine in a Raspberry Pi device that needs an image for ARM.

###See the logs of each container:

To see the logs of each container do:

```
docker ps -a
```

This will list the containers, look for the ones that correspond to the services, for this look at the last column
which have the name of the container, it should be something like: "akkaclusterswarm_master".

Then do the following to see in real time the logs of the container:

```
docker logs --follow containerid   
```
Replacing the corresponding container id.

###Use docker swarm visualizer

There is a very nice visualizer for the containers/services deployed on a docker swarm.
Looks at the repo of the project: 

[Docker Swarm Visualizer](https://github.com/dockersamples/docker-swarm-visualizer)


### Description

#### Application workflow

1. A sensor actor in the IoT Cluster generates data readings to be processed. These readings consist of an array of values. This array of values is put in a “envelope” called SensorData, which includes other relevant information with respect to the data readings, such as the sensorId.
2. The sensor publishes the SensorData to the corresponding MQTT topic on the IoT Cluster.
3. The IoT manager, which is subscribed to this topic, receives the SensorData and proceeds to send the SensorData to the Master Cluster.
4. The Master Cluster, through its singleton actor, upon reception of the SensorData adds the SensorData to a queue of sensor data waiting to be send to the Worker Cluster.
5. The Singleton actor takes the SensorData from the queue (assuming it was the only element in the queue, otherwise it would wait accordingly), and sends it to an available Worker node in the Cluster Worker.
6. The Worker node, through its ShardRegion actor, receives the SensorData and extracts the entityId, shardId and message from the SensorData using the MessageExtractor.
7. The ShardRegion actor, with the help of the information of the entityId and shardId, proceeds to forward the SensorData to the corresponding Shard. The Shard contains all entity actors that correspond to each sensor. In this case, the corresponding sensor entity actor, called Worker, receives the SensorData. The location of the entity actor can be in the same node that received the message or in another node in the cluster. The owner of this information is the ShardCoordinator of the cluster which resolves these concerns.
8. The Worker entity receives the SensorData and starts the handling of the forecasting tasks. First, it creates an Aggregator actor to aggregate the results of the forecasting tasks. Then, it starts sending all the forecasting tasks, one by one, to the WorkerProcessor actor. The WorkerProcessor routes each forecasting task to one of the deployed routees of the router, distributing all the computing tasks of the SensorData among all cluster nodes.
9. The WorkerProcessor routee receives the SensorData and proceeds to do the corresponding computation task. Once it is done with the processing, it sends the results to the Aggregator actor.
10. The Aggregator actor, receives one by one the results of the forecasting tasks. Once the last one is received, it sends the final result back to the Worker entity actor.
11. The Worker entity, upon receiving the final result, sends the result back to the Master Cluster.
12. The Master Cluster, through the singleton actor of the cluster, receives the result and publish it to a specific topic using a mediator actor that handles the publish-subscribe mechanism in the cluster.
13. A ResultProcessor actor, that is subscribed to publish-subscribe topic, receives the result and sends it back to the IoT manager.
14. The IoT manager receives the results and send it back to the appropriate Sensor that created the data.
15. Finally, the Sensor receives the results of the processing. 

#### Project Structure

##### actors
 Package containing all the actors of the system (including state classes that are not actors). This package
contains other subpackages ordered by the clusters they work in:

* iot: Contains the actors of the IoT cluster, and the SensorData class that represents the sensor data.

* master: Contains the actors of the Master cluster. Includes all the different message protocols used for communication
between the Master cluster and other clusters. Also contains State classes: SensorDataState.java and WorkerRegionState.java

The Master cluster uses a Cluster singleton actor to keep control of all the sensor data received.
This cluster uses the Cassandra cluster to persist the state in case of recover.

* worker: Contains the actors of the Worker cluster. Including the message protocols to communicate with the Master cluster.
It also contains the WorkProcessor actor. Which is a self-contained router actor. This actor handles the distribution
of tasks assigning them to its routees deployed on all the nodes of the cluster. See the worker.conf configuration file.

##### main
Package containing all the classes that start the whole system. There are 4 classes. One general, and then one for each of the
clusters: MainIoT, MainMaster, MainWorker

##### utils
Package containing the util classes for the different clusters. ConfigUtils.java helps to load the different configuration
classes. The MqttPayloadManager.java helps to serialize and deserialize messages that go through the MQTT broker.

#### Messages
 
All messages of the application are grouped in interfaces withing their package. The names of the protocols
are self-descriptive and helps to understand what type of messages they are.

### Configuration files

There are 3 different configuration files, one for each cluster.
The configuration files use the environmental variables defined in the docker-compose.yml file to set the corresponding
IP addressees and ports, so that the communication can be done remotely using the created Docker overlay
network.

#### application.conf

This is the general configuration file. It contains all the configuration for the MainCluster including:

* Distributed Journal: Cassandra
* Extensions:  "akka.cluster.pubsub.DistributedPubSub", "akka.cluster.client.ClusterClientReceptionist"

#### iotmanager.conf

This is the configuration file for the IoT cluster. It contains all the configuration to initialize the IoT cluster. This includes:

* Initial contact list for the cluster-client mechanism
* MQTT topic and broker address

#### worker.conf

This is the configuration file for the Worker cluster. It contains all the configuration to initialize the Worker cluster. This includes:

* Initial contact list for the cluster-client mechanism
* Routing configuration for the cluster-aware routers mechanism

