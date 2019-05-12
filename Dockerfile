#Use to build docker image for linux machine
#FROM gradle
#Use to build docker image for raspberrypi model 3B+
FROM arm32v7/gradle
COPY --chown=gradle . /akka/wsn/app
WORKDIR /akka/wsn/app
