package es.us.dad;

import java.util.Arrays;


import es.us.dad.mysql.rest.RestAPIVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class Launcher extends AbstractVerticle {

	@Override
	public void start(Promise<Void> startFuture) throws Exception {
		Promise<Void> deviceVerticle = Promise.promise();
		Promise<Void> sensorVerticle = Promise.promise();
		Promise<Void> actuatorVerticle = Promise.promise();
		Promise<Void> groupVerticle = Promise.promise();
		Promise<Void> sensorValueVerticle = Promise.promise();
		Promise<Void> actuatorStatusVerticle = Promise.promise();
		Promise<Void> databaseVerticle = Promise.promise();
		Promise<Void> restApiVerticle = Promise.promise();
		Promise<Void> mqttClientUtil = Promise.promise();

		CompositeFuture compositeFuture = CompositeFuture
				.all(Arrays.asList(deviceVerticle.future(), sensorVerticle.future(), actuatorVerticle.future(),
						groupVerticle.future(), sensorValueVerticle.future(), actuatorStatusVerticle.future(),
						databaseVerticle.future(), restApiVerticle.future(), mqttClientUtil.future()));

		compositeFuture.onComplete(handler -> {
			if (handler.succeeded())
				startFuture.complete();
			else
				startFuture.fail(handler.cause());
		});


		vertx.deployVerticle(new RestAPIVerticle(), handlerController -> {
			if (handlerController.succeeded())
				restApiVerticle.complete();
			else
				restApiVerticle.fail(handlerController.cause());
		});
	
	}		
		
	@Override
	public void stop(Future<Void> stopFuture) throws Exception {
		super.stop(stopFuture);
	}

}
