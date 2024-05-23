package es.us.dad.mqtt;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;


public class MqttClientUtil {

	protected static transient MqttClient mqttClient;

	private static transient MqttClientUtil mqttClientClass = null;

	private MqttClientUtil(Vertx vertx) {
		mqttClient = MqttClient.create(vertx, new MqttClientOptions());
		mqttClient.connect(1883, "localhost", s -> {
			if (s.succeeded()) {
				System.out.println("Sucessfully connected to MQTT brocker");
			} else {
				System.err.println(s.cause());
			}
		});
	}

	public void publishMqttMessage(String topic, String payload, Handler<AsyncResult<Integer>> handler) {
		mqttClient.publish(topic, Buffer.buffer(payload), MqttQoS.AT_LEAST_ONCE, false, false, handler);
	}

	public void subscribeMqttTopic(String topic, Handler<AsyncResult<Integer>> handler) {
		mqttClient.subscribe(topic, MqttQoS.AT_LEAST_ONCE.value(), handler);
	}

	public void unsubscribeMqttTopic(String topic) {
		mqttClient.unsubscribe(topic);
	}

	public static MqttClientUtil getInstance(Vertx vertx) {
		if (mqttClientClass == null) {
			mqttClientClass = new MqttClientUtil(vertx);
		}
		return mqttClientClass;
	}

}
