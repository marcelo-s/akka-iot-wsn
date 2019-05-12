package actors.worker;

import actors.iot.SensorData;
import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.github.signaflo.timeseries.TimePeriod;
import com.github.signaflo.timeseries.TimeSeries;
import com.github.signaflo.timeseries.forecast.Forecast;
import com.github.signaflo.timeseries.model.arima.Arima;
import com.github.signaflo.timeseries.model.arima.ArimaOrder;

import java.util.List;

public class WorkProcessor extends AbstractActor {

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props() {
        return Props.create(WorkProcessor.class);
    }

    public WorkProcessor() {

    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(WorkerProtocol.SensorDataModelTask.class, this::handleSensorDataModelTask)
                .matchAny(this::handleAny)
                .build();
    }

    private void handleAny(Object o) {
        log.warning("Work Processor >>> HANDLE ANY - SensorData processor received unknown object of class : {}", o.getClass().getName());
    }

    private void handleSensorDataModelTask(WorkerProtocol.SensorDataModelTask sensorDataModelTask) {
        log.info("Worker routee >>> Processing SensorDataId : {} and ARIMA model : {}",
                sensorDataModelTask.getSensorData().getDataId(),
                sensorDataModelTask.getArimaOrderParams());
        Arima computedModel = computeModel(sensorDataModelTask);
        // The sender() is the actor reference of the WorkAggregator
        sender().tell(new WorkerProtocol.SensorDataModelProcessed(
                sensorDataModelTask.getArimaOrderParams(),
                predictWithModel(computedModel),
                computedModel.aic()
        ), self());
    }


    private Arima computeModel(WorkerProtocol.SensorDataModelTask sensorDataModelTask) {
        List<Integer> params = sensorDataModelTask.getArimaOrderParams();
        int p = params.get(0);
        int d = params.get(1);
        int q = params.get(2);
        ArimaOrder arimaOrder = ArimaOrder.order(p, d, q);
        SensorData sensorData = sensorDataModelTask.getSensorData();

        TimePeriod hour = TimePeriod.oneHour();
        TimeSeries series = TimeSeries.from(hour, sensorData.getDataReadings());
        TimePeriod day = TimePeriod.oneDay();
        return Arima.model(series, arimaOrder, day);

//        Instant start = Instant.now();


//        double aic1 = model1.aic();
//
//        Instant finish = Instant.now();
//
//        long timeElapsed = Duration.between(start, finish).toMillis();  //in millis
//
//        System.out.println("Duration: " + timeElapsed);
//
//        return predictWithModel(model1);
    }

    private double[] predictWithModel(Arima model) {
        Forecast forecast = model.forecast(7);
        TimeSeries timeSeries = forecast.pointEstimates();
        return timeSeries.asArray();
    }
}
