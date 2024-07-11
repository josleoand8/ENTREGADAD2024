package es.us.dad.mysql.rest;

import java.util.Calendar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import es.us.dad.mysql.entities.Actuator;
import es.us.dad.mysql.entities.ActuatorStatus;
import es.us.dad.mysql.entities.Device;
import es.us.dad.mysql.entities.Group;
import es.us.dad.mysql.entities.Sensor;
import es.us.dad.mysql.entities.SensorType;
import es.us.dad.mysql.entities.SensorValue;
import es.us.dad.mqtt.MqttClientUtil;
//import es.us.dad.mysql.messages.DatabaseEntity;
//import es.us.dad.mysql.messages.DatabaseMessage;
//import es.us.dad.mysql.messages.DatabaseMessageIdAndActuatorType;
//import es.us.dad.mysql.messages.DatabaseMessageIdAndSensorType;
//import es.us.dad.mysql.messages.DatabaseMessageLatestValues;
//import es.us.dad.mysql.messages.DatabaseMessageType;
//import es.us.dad.mysql.messages.DatabaseMethod;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.mysqlclient.MySQLClient;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;

public class RestAPIVerticle extends AbstractVerticle {

	private transient Gson gson;
	MySQLPool mySqlClient;
	
	protected static transient MqttClient mqttClient;

	private static transient MqttClientUtil mqttClientClass = null;

	@Override
	public void start(Promise<Void> startFuture) {
		
		mqttClient = MqttClient.create(vertx, new MqttClientOptions());
		mqttClient.connect(1883, "172.20.10.3", s -> {
			if (s.succeeded()) {
				System.out.println("Sucessfully connected to MQTT brocker");
			} else {
				System.err.println(s.cause());
			}
		});
		
		MySQLConnectOptions connectOptions = new MySQLConnectOptions().setPort(3306).setHost("localhost")
				.setDatabase("dad").setUser("root").setPassword("Josemaria2");

		PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

		mySqlClient = MySQLPool.pool(vertx, connectOptions, poolOptions);

		// Instantiating a Gson serialize object using specific date format
		gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();

		// Defining the router object
		Router router = Router.router(vertx);

		// Handling any server startup result
		HttpServer httpServer = vertx.createHttpServer();
		httpServer.requestHandler(router::handle).listen(8080, result -> {
			if (result.succeeded()) {
				System.out.println("API Rest is listening on port 8080");
				startFuture.complete();
			} else {
				startFuture.fail(result.cause());
			}
		});

		// Defining URI paths for each method in RESTful interface, including body
		// handling
		router.route("/api*").handler(BodyHandler.create());

		// Endpoint definition for CRUD ops
		
		//Sensors
		router.get("/api/sensors/:sensor").handler(this::getSensorById); //OK
		router.post("/api/sensors").handler(this::addSensor); //OK
		router.delete("/api/sensors/:sensorid").handler(this::deleteSensor); //OK
		router.put("/api/sensors/:sensorid").handler(this::putSensor); //OK
		
		//Groups 
		router.get("/api/groups/:groupid").handler(this::getGroupById); //OK
		router.post("/api/groups").handler(this::addGroup); //OK
		router.delete("/api/groups/:groupid").handler(this::deleteGroup); //OK
		router.put("/api/groups/:groupid").handler(this::putGroup); //OK
		
		//Devices
		router.get("/api/groups/:groupid/devices").handler(this::getDevicesFromGroup); //OK
		router.get("/api/devices/:device").handler(this::getDeviceById); //OK
		router.post("/api/devices").handler(this::addDevice); //OK
		router.delete("/api/devices/:deviceid").handler(this::deleteDevice); //OK
		router.put("/api/devices/:deviceid").handler(this::putDevice); //OK
		router.get("/api/devices/:deviceid/sensors").handler(this::getSensorsFromDevice); //OK
		router.get("/api/devices/:deviceid/actuators").handler(this::getActuatorsFromDevice); //OK
		router.get("/api/devices/:deviceid/sensors/:type").handler(this::getSensorsFromDeviceAndType); //OK
		
		
		
		//Actuadores
		router.get("/api/actuators/:actuator").handler(this::getActuatorById); //OK
		router.post("/api/actuators").handler(this::addActuator); //OK
		router.delete("/api/actuators/:actuatorid").handler(this::deleteActuator); //OK
		router.put("/api/actuators/:actuatorid").handler(this::putActuator); //OK

		//SensorValues
		router.post("/api/sensor_values").handler(this::addSensorValue); //OK
		router.delete("/api/sensor_values/:sensorvalueid").handler(this::deleteSensorValue); //OK
		router.get("/api/sensor_values/:sensorid/last").handler(this::getLastSensorValue); //OK
		router.get("/api/sensor_values/:sensorid/latest/:limit").handler(this::getLatestSensorValue); //OK
		
		
		//ActuadorValues
		router.post("/api/actuator_states").handler(this::addActuatorStatus); //OK
		router.delete("/api/actuator_states/:actuatorstatusid").handler(this::deleteActuatorStatus); //OK
		router.get("/api/actuator_states/:actuatorid/last").handler(this::getLastActuatorStatus); //OK
	}


	//APISensores
    
    private void getSensorById(RoutingContext routingContext) {
        mySqlClient.getConnection(connection -> {
            int idSensor = Integer.parseInt(routingContext.request().getParam("sensor")); // Accedemos al parámetro "id" en lugar de "sensor"
            if (connection.succeeded()) {
                connection.result().preparedQuery("SELECT * FROM dad.sensors WHERE idSensor = ?;", Tuple.of(idSensor), res -> {
                    if (res.succeeded()) {
                        // Get the result set
                        RowSet<Row> resultSet = res.result();
                        System.out.println(resultSet.size());
                        List<Sensor> result = new ArrayList<>();
                        for (Row elem : resultSet) {
                        	result.add(new Sensor(elem.getString("name"), elem.getInteger("idDevice"), elem.getString("sensorType"),
									 elem.getBoolean("removed")));
                        }
                        routingContext.response()
                                .putHeader("content-type", "application/json; charset=utf-8")
                                .setStatusCode(200)
                                .end(gson.toJson(result));
                    } else {
                        System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
                    }
                    // Cerramos la conexión después de obtener los resultados
                    connection.result().close();
                });
            } else {
                System.out.println(connection.cause().toString());
                routingContext.response().setStatusCode(500).end("Error con la conexión: " + connection.cause().getMessage());
            }
        });
    }
    
    protected void addSensor(RoutingContext routingContext) {
    	
    	 final Sensor sensor = gson.fromJson(routingContext.getBodyAsString(),
		    		Sensor.class);
    	 
    	 mySqlClient.preparedQuery(
 				"INSERT INTO dad.sensors (name, idDevice, sensorType, removed) VALUES (?,?,?,?);",
 				Tuple.of(sensor.getName(), sensor.getIdDevice(), sensor.getSensorType().name(), sensor.isRemoved()),
 				res -> {
 					if (res.succeeded()) {
 						String topic =  sensor.getIdDevice().toString();
 	                    String payload = gson.toJson(sensor);
 	                    mqttClient.publish(topic, Buffer.buffer(payload), MqttQoS.AT_LEAST_ONCE, false, false);
						long lastInsertId = res.result().property(MySQLClient.LAST_INSERTED_ID);
						sensor.setIdSensor((int) lastInsertId);
						routingContext.response().setStatusCode(201).putHeader("content-type",
                                "application/json; charset=utf-8").end("Sensor añadido correctamente");
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al añadir el sensor: " + res.cause().getMessage());
					}
				});
	}
    
 
    
    
    protected void putSensor(RoutingContext routingContext) {
    	
    	
    	
    	final Sensor sensor = gson.fromJson(routingContext.getBodyAsString(), Sensor.class);
    	
		mySqlClient.preparedQuery(
				"UPDATE dad.sensors g SET name = COALESCE(?, g.name), idDevice = COALESCE(?, g.idDevice), sensorType = COALESCE(?, g.sensorType), removed = COALESCE(?, g.removed) WHERE idSensor = ?;",
				Tuple.of(sensor.getName(), sensor.getIdDevice(), sensor.getSensorType(), sensor.isRemoved(),
						sensor.getIdSensor()),
				res -> {
					if (res.succeeded()) {
						if (res.result().rowCount() > 0) {
	                        routingContext.response()
	                            .setStatusCode(200)
	                            .putHeader("content-type", "application/json; charset=utf-8")
	                            .end(gson.toJson(sensor));
	                    } 
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
		  	              routingContext.response()
		  	                .putHeader("content-type", "application/json; charset=utf-8")
		  	                        .setStatusCode(404)
		  	                        .end("Error al actualizar los sensores: " + res.cause().getMessage());
					}
				});
		
		
		
	}
    protected void deleteSensor(RoutingContext routingContext) {
    	
    	 int idSensor = Integer.parseInt(routingContext.request().getParam("sensorid"));
		
		mySqlClient.preparedQuery("DELETE FROM dad.sensors WHERE idSensor = ?;", Tuple.of(idSensor), res -> {
			if (res.succeeded()) {
				 if (res.result().rowCount() > 0) {
                     routingContext.response()
                         .setStatusCode(200)
                         .putHeader("content-type", "application/json; charset=utf-8")
                         .end(gson.toJson(new JsonObject().put("message", "Sensor eliminado correctamente")));
                 } 
			} else {
				System.out.println("Error: " + res.cause().getLocalizedMessage());
	            routingContext.response()
	                    .setStatusCode(500)
	                    .end("Error al conectar con la base de datos: ");
			}
		});
	}
    
    
    //Grupos
    
	protected void addGroup(RoutingContext routingContext) {
		
		final Group group = gson.fromJson(routingContext.getBodyAsString(),
	    		Group.class);
		
		mySqlClient.preparedQuery(
				"INSERT INTO dad.groups (name, mqttChannel, lastMessageReceived) VALUES (?,?,?);",
				Tuple.of(group.getName(), group.getMqttChannel(), group.getLastMessageReceived()), res -> {
					if (res.succeeded()) {
						long lastInsertId = res.result().property(MySQLClient.LAST_INSERTED_ID);
						group.setIdGroup((int) lastInsertId);
						RowSet<Row> resultSet = res.result();
                        System.out.println(resultSet.size());
                        List<Group> result = new ArrayList<>();
                        for (Row elem : resultSet) {
                        	result.add(new Group(elem.getInteger("idGroup"), elem.getString("mqttChannel"), elem.getString("name"),
									 elem.getLong("lastMessageReceived")));
                        }
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al añadir el sensor: " + res.cause().getMessage());
					}
				});
	}
	
	protected void getGroupById(RoutingContext routingContext) {
		
		int groupId = Integer.parseInt(routingContext.request().getParam("groupid"));
		
		mySqlClient.preparedQuery("SELECT * FROM dad.groups WHERE idGroup = ?;", Tuple.of(groupId), res -> {
			if (res.succeeded()) {
				// Get the result set
				 RowSet<Row> resultSet = res.result();
                 System.out.println(resultSet.size());
                 List<Group> result = new ArrayList<>();
                 for (Row elem : resultSet) {
                 	result.add(new Group(elem.getInteger("idGroup"), elem.getString("mqttChannel"), elem.getString("name"),
							 elem.getLong("lastMessageReceived")));
                 }
				routingContext.response()
                .putHeader("content-type", "application/json; charset=utf-8")
                .setStatusCode(200)
                .end(gson.toJson(result));
			} else {
				 System.out.println("Error: " + res.cause().getLocalizedMessage());
                 routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
			}
		});
	}
	
	protected void deleteGroup(RoutingContext routingContext) {
		int groupId = Integer.parseInt(routingContext.request().getParam("groupid"));
		mySqlClient.preparedQuery("DELETE FROM dad.groups WHERE idGroup = ?;", Tuple.of(groupId), res -> {
			if (res.succeeded()) {
				if (res.result().rowCount() > 0) {
                    routingContext.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(gson.toJson(new JsonObject().put("message", "Grupo eliminado correctamente")));
                } 
			} else {
				System.out.println("Error: " + res.cause().getLocalizedMessage());
	            routingContext.response()
	                    .setStatusCode(500)
	                    .end("Error al conectar con la base de datos: ");
			}
		});
	}
	
	protected void putGroup(RoutingContext routingContext) {
		final Group group = gson.fromJson(routingContext.getBodyAsString(), Group.class);
		mySqlClient.preparedQuery(
				"UPDATE dad.groups g SET name = COALESCE(?, g.name), mqttChannel = COALESCE(?, g.mqttChannel), lastMessageReceived = COALESCE(?, g.lastMessageReceived) WHERE idGroup = ?;",
				Tuple.of(group.getName(), group.getMqttChannel(), group.getLastMessageReceived(), group.getIdGroup()),
				res -> {
					if (res.succeeded()) {
						if (res.result().rowCount() > 0) {
	                        routingContext.response()
	                            .setStatusCode(200)
	                            .putHeader("content-type", "application/json; charset=utf-8")
	                            .end(gson.toJson(group));
	                    } 
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
		  	              routingContext.response()
		  	                .putHeader("content-type", "application/json; charset=utf-8")
		  	                        .setStatusCode(404)
		  	                        .end("Error al actualizar los sensores: " + res.cause().getMessage());
					}
				});
	}
	
	
	//Devices
	
	protected void getDevicesFromGroup(RoutingContext routingContext) {
		int groupId = Integer.parseInt(routingContext.request().getParam("groupid"));
		mySqlClient.preparedQuery("SELECT * FROM dad.devices WHERE idGroup = ?;", Tuple.of(groupId), res -> {
			if (res.succeeded()) {
				// Get the result set
				RowSet<Row> resultSet = res.result();
				System.out.println(resultSet.size());
				List<Device> result = new ArrayList<>();
				for (Row elem : resultSet) {
					result.add(new Device(elem.getInteger("idDevice"), elem.getString("deviceSerialId"),
							elem.getString("name"), elem.getInteger("idGroup"), elem.getString("mqttChannel"),
							elem.getLong("lastTimestampSensorModified"),
							elem.getLong("lastTimestampActuatorModified")));
				}

				routingContext.response()
                .putHeader("content-type", "application/json; charset=utf-8")
                .setStatusCode(200)
                .end(gson.toJson(result));
			} else {
				System.out.println("Error: " + res.cause().getLocalizedMessage());
                routingContext.response().setStatusCode(500).end("Error al obtener los devices: " + res.cause().getMessage());
			}
		});
	}
	
	protected void getDeviceById(RoutingContext routingContext) {
		int deviceId = Integer.parseInt(routingContext.request().getParam("device"));
		mySqlClient.preparedQuery("SELECT * FROM dad.devices WHERE idDevice = ?;", Tuple.of(deviceId), res -> {
			 if (res.succeeded()) {
                 // Get the result set
                 RowSet<Row> resultSet = res.result();
                 System.out.println(resultSet.size());
                 List<Device> result = new ArrayList<>();
                 for (Row elem : resultSet) {
                 	result.add(new Device(elem.getInteger("idDevice"), elem.getString("deviceSerialId"),
							elem.getString("name"), elem.getInteger("idGroup"), elem.getString("mqttChannel"),
							elem.getLong("lastTimestampSensorModified"), elem.getLong("lastTimestampActuatorModified")));
                 }
                 routingContext.response()
                         .putHeader("content-type", "application/json; charset=utf-8")
                         .setStatusCode(200)
                         .end(gson.toJson(result));
             } else {
                 System.out.println("Error: " + res.cause().getLocalizedMessage());
                 routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
             }
		});
	}
	
	protected void addDevice(RoutingContext routingContext) {
		final Device device = gson.fromJson(routingContext.getBodyAsString(), Device.class);
		mySqlClient.preparedQuery(
				"INSERT INTO dad.devices (deviceSerialId, name, idGroup, mqttChannel, lastTimestampSensorModified,"
						+ " lastTimestampActuatorModified) VALUES (?,?,?,?,?,?);",
				Tuple.of(device.getDeviceSerialId(), device.getName(), device.getIdGroup(), device.getMqttChannel(),
						device.getLastTimestampSensorModified(), device.getLastTimestampActuatorModified()),
				res -> {
					if (res.succeeded()) {
						long lastInsertId = res.result().property(MySQLClient.LAST_INSERTED_ID);
						device.setIdGroup((int) lastInsertId);
						RowSet<Row> resultSet = res.result();
                        System.out.println(resultSet.size());
                        List<Device> result = new ArrayList<>();
                        for (Row elem : resultSet) {
                        	result.add(new Device(elem.getInteger("idDevice"), elem.getString("deviceSerialId"),
        							elem.getString("name"), elem.getInteger("idGroup"), elem.getString("mqttChannel"),
        							elem.getLong("lastTimestampSensorModified"), elem.getLong("lastTimestampActuatorModified")));
                        }
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al añadir el sensor: " + res.cause().getMessage());
					}
				});
	}
	
	protected void deleteDevice(RoutingContext routingContext) {
		int deviceId = Integer.parseInt(routingContext.request().getParam("deviceid"));
		mySqlClient.preparedQuery("DELETE FROM dad.devices WHERE idDevice = ?;", Tuple.of(deviceId), res -> {
			if (res.succeeded()) {
				if (res.result().rowCount() > 0) {
                    routingContext.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(gson.toJson(new JsonObject().put("message", "Dispositivo eliminado correctamente")));
                } 
			} else {
				System.out.println("Error: " + res.cause().getLocalizedMessage());
	            routingContext.response()
	                    .setStatusCode(500)
	                    .end("Error al conectar con la base de datos: ");
			}
		});
	}
	
	protected void putDevice(RoutingContext routingContext) {
		final Device device = gson.fromJson(routingContext.getBodyAsString(), Device.class);
		mySqlClient.preparedQuery(
				"UPDATE dad.devices g SET deviceSerialId = COALESCE(?, g.deviceSerialId), name = COALESCE(?, g.name), idGroup = COALESCE(?, g.idGroup), mqttChannel = COALESCE(?, g.mqttChannel), lastTimestampSensorModified = COALESCE(?, g.lastTimestampSensorModified), lastTimestampActuatorModified = COALESCE(?, g.lastTimestampActuatorModified) WHERE idDevice = ?;",
				Tuple.of(device.getDeviceSerialId(), device.getName(), device.getIdGroup(), device.getMqttChannel(),
						device.getLastTimestampSensorModified(), device.getLastTimestampActuatorModified(),
						device.getIdDevice()),
				res -> {
					if (res.result().rowCount() > 0) {
                        routingContext.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(gson.toJson(device));
                    } else {
					System.out.println("Error: " + res.cause().getLocalizedMessage());
	  	              routingContext.response()
	  	                .putHeader("content-type", "application/json; charset=utf-8")
	  	                        .setStatusCode(404)
	  	                        .end("Error al actualizar los sensores: " + res.cause().getMessage());
				}
				});
	}


//	private void putDevice(RoutingContext routingContext) {
//		final Device device = gson.fromJson(routingContext.getBodyAsString(), Device.class);
//		int deviceId = Integer.parseInt(routingContext.request().getParam("deviceid"));
//
//		if (device == null) {
//			routingContext.response().putHeader("content-type", "application/json").setStatusCode(500).end();
//			return;
//		}
//
//		device.setIdGroup(deviceId);
//		DatabaseMessage databaseMessage = new DatabaseMessage(DatabaseMessageType.UPDATE, DatabaseEntity.Device,
//				DatabaseMethod.EditDevice, gson.toJson(device));
//
//		vertx.eventBus().request(RestEntityMessage.Device.getAddress(), gson.toJson(databaseMessage), handler -> {
//			if (handler.succeeded()) {
//				DatabaseMessage responseMessage = deserializeDatabaseMessageFromMessageHandler(handler);
//				routingContext.response().putHeader("content-type", "application/json").setStatusCode(201)
//						.end(gson.toJson(responseMessage.getResponseBodyAs(Device.class)));
//			} else {
//				routingContext.response().putHeader("content-type", "application/json").setStatusCode(500).end();
//			}
//		});
//	}


	
	protected void getSensorsFromDevice(RoutingContext routingContext) {
		int deviceId = Integer.parseInt(routingContext.request().getParam("deviceid"));
		mySqlClient.preparedQuery("SELECT * FROM dad.sensors WHERE idDevice = ?;", Tuple.of(deviceId), res -> {
			if (res.succeeded()) {
				// Get the result set
				RowSet<Row> resultSet = res.result();
				List<Sensor> result = new ArrayList<>();
				for (Row elem : resultSet) {
					result.add(new Sensor(elem.getInteger("idSensor"), elem.getString("name"),
							elem.getInteger("idDevice"), elem.getString("sensorType"), elem.getBoolean("removed")));
				}

				 routingContext.response()
                 .putHeader("content-type", "application/json; charset=utf-8")
                 .setStatusCode(200)
                 .end(gson.toJson(result));
     } else {
         System.out.println("Error: " + res.cause().getLocalizedMessage());
         routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
     }
		});
	}
	
	protected void getActuatorsFromDevice(RoutingContext routingContext) {
		int deviceId = Integer.parseInt(routingContext.request().getParam("deviceid"));
		mySqlClient.preparedQuery("SELECT * FROM dad.actuators WHERE idDevice = ?;", Tuple.of(deviceId),
				res -> {
					if (res.succeeded()) {
						// Get the result set
						RowSet<Row> resultSet = res.result();
						List<Actuator> result = new ArrayList<>();
						for (Row elem : resultSet) {
							result.add(new Actuator(elem.getInteger("idActuator"), elem.getString("name"),
									elem.getInteger("idDevice"), elem.getString("actuatorType"),
									elem.getBoolean("removed")));
						}

						routingContext.response()
		                 .putHeader("content-type", "application/json; charset=utf-8")
		                 .setStatusCode(200)
		                 .end(gson.toJson(result));
		     } else {
		         System.out.println("Error: " + res.cause().getLocalizedMessage());
		         routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
		     }
				});
	}
	

	protected void getSensorsFromDeviceAndType(RoutingContext routingContext) {
		int deviceId = Integer.parseInt(routingContext.request().getParam("deviceid"));
		SensorType type = SensorType.valueOf(routingContext.request().getParam("type"));
		mySqlClient.preparedQuery("SELECT * FROM dad.sensors WHERE idDevice = ? AND sensorType = ?;",
				Tuple.of(deviceId, type), res -> {
					if (res.succeeded()) {
						// Get the result set
						RowSet<Row> resultSet = res.result();
						List<Sensor> result = new ArrayList<>();
						for (Row elem : resultSet) {
							result.add(new Sensor(elem.getInteger("idSensor"), elem.getString("name"),
									elem.getInteger("idDevice"), elem.getString("sensorType"),
									elem.getBoolean("removed")));
						}

						routingContext.response()
		                 .putHeader("content-type", "application/json; charset=utf-8")
		                 .setStatusCode(200)
		                 .end(gson.toJson(result));
		     } else {
		         System.out.println("Error: " + res.cause().getLocalizedMessage());
		         routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
		     }
				});
	}
	
	
	
	//Actuators
	
	protected void getActuatorById(RoutingContext routingContext) {
		int actuatorId = Integer.parseInt(routingContext.request().getParam("actuator"));
		mySqlClient.preparedQuery("SELECT * FROM dad.actuators WHERE idActuator = ?;", Tuple.of(actuatorId),
				res -> {
					if (res.succeeded()) {
						// Get the result set
						 RowSet<Row> resultSet = res.result();
		                 System.out.println(resultSet.size());
		                 List<Actuator> result = new ArrayList<>();
		                 for (Row elem : resultSet) {
		                 	result.add(new Actuator(elem.getInteger("idActuator"), elem.getString("name"),
									elem.getInteger("idDevice"), elem.getString("actuatorType"),
									elem.getBoolean("removed")));
		                 }
						routingContext.response()
		                .putHeader("content-type", "application/json; charset=utf-8")
		                .setStatusCode(200)
		                .end(gson.toJson(result));
					} else {
						 System.out.println("Error: " + res.cause().getLocalizedMessage());
		                 routingContext.response().setStatusCode(500).end("Error al obtener los actuadores: " + res.cause().getMessage());
					}
				});
	}

	protected void addActuator(RoutingContext routingContext) {
		final Actuator actuator = gson.fromJson(routingContext.getBodyAsString(), Actuator.class);
		mySqlClient.preparedQuery(
				"INSERT INTO dad.actuators (name, idDevice, removed) VALUES (?,?,?,?);",
				Tuple.of(actuator.getName(), actuator.getIdDevice(),
						actuator.isRemoved()),
				res -> {
					if (res.succeeded()) {
						long lastInsertId = res.result().property(MySQLClient.LAST_INSERTED_ID);
						actuator.setIdActuator((int) lastInsertId);
						routingContext.response().setStatusCode(201).putHeader("content-type",
                                "application/json; charset=utf-8").end("Actuador añadido correctamente");
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al añadir el sensor: " + res.cause().getMessage());
					}
				});
	}
	
	protected void putActuator(RoutingContext routingContext) {
		final Actuator actuator = gson.fromJson(routingContext.getBodyAsString(), Actuator.class);
		
		mySqlClient.preparedQuery(
				"UPDATE dad.actuators g SET name = COALESCE(?, g.name), idDevice = COALESCE(?, g.idDevice),  removed = COALESCE(?, g.removed) WHERE idActuator = ?;",
				Tuple.of(actuator.getName(), actuator.getIdDevice(), actuator.isRemoved(),
						actuator.getIdActuator()),
				res -> {
					if (res.succeeded()) {
						if (res.result().rowCount() > 0) {
	                        routingContext.response()
	                            .setStatusCode(200)
	                            .putHeader("content-type", "application/json; charset=utf-8")
	                            .end(gson.toJson(actuator));
	                    } 
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
		  	              routingContext.response()
		  	                .putHeader("content-type", "application/json; charset=utf-8")
		  	                        .setStatusCode(404)
		  	                        .end("Error al actualizar los sensores: " + res.cause().getMessage());
					}
				});
	}
	
	protected void deleteActuator(RoutingContext routingContext) {
		int actuatorId = Integer.parseInt(routingContext.request().getParam("actuatorid"));
		mySqlClient.preparedQuery("DELETE FROM dad.actuators WHERE idActuator = ?;", Tuple.of(actuatorId),
				res -> {
					if (res.succeeded()) {
						if (res.result().rowCount() > 0) {
		                    routingContext.response()
		                        .setStatusCode(200)
		                        .putHeader("content-type", "application/json; charset=utf-8")
		                        .end(gson.toJson(new JsonObject().put("message", "Actuador eliminado correctamente")));
		                } 
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
			            routingContext.response()
			                    .setStatusCode(500)
			                    .end("Error al conectar con la base de datos: ");
					}
				});
	}
	
//	private void putActuator(RoutingContext routingContext) {
//		final Actuator actuator = gson.fromJson(routingContext.getBodyAsString(), Actuator.class);
//		int actuatorId = Integer.parseInt(routingContext.request().getParam("actuatorid"));
//
//		if (actuator == null) {
//			routingContext.response().putHeader("content-type", "application/json").setStatusCode(500).end();
//			return;
//		}
//
//		actuator.setIdActuator(actuatorId);
//		DatabaseMessage databaseMessage = new DatabaseMessage(DatabaseMessageType.UPDATE, DatabaseEntity.Actuator,
//				DatabaseMethod.EditActuator, gson.toJson(actuator));
//
//		vertx.eventBus().request(RestEntityMessage.Sensor.getAddress(), gson.toJson(databaseMessage), handler -> {
//			if (handler.succeeded()) {
//				DatabaseMessage responseMessage = deserializeDatabaseMessageFromMessageHandler(handler);
//				routingContext.response().putHeader("content-type", "application/json").setStatusCode(201)
//						.end(gson.toJson(responseMessage.getResponseBodyAs(Actuator.class)));
//			} else {
//				routingContext.response().putHeader("content-type", "application/json").setStatusCode(500).end();
//			}
//		});
//	}
	
	
	//SensorValues
	
	protected void getLastSensorValue(RoutingContext routingContext) {
		int sensorId = Integer.parseInt(routingContext.request().getParam("sensorid"));
		mySqlClient.preparedQuery(
				"SELECT * FROM dad.sensorValues WHERE idSensor = ? ORDER BY `timestamp` ASC LIMIT 1;",
				Tuple.of(sensorId), res -> {
					if (res.succeeded()) {
						// Get the result set
						RowSet<Row> resultSet = res.result();
                        System.out.println(resultSet.size());
                        List<SensorValue> result = new ArrayList<>();
                        for (Row elem : resultSet) {
                        	result.add(new SensorValue(elem.getInteger("idSensorValue"), elem.getFloat("value"), elem.getInteger("idSensor"),
									elem.getInteger("idSensor"), elem.getLong("timestamp"), elem.getBoolean("removed")));
                        }
                        routingContext.response()
                                .putHeader("content-type", "application/json; charset=utf-8")
                                .setStatusCode(200)
                                .end(gson.toJson(result));
                    } else {
                        System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
                    }
				});
	}
	
	protected void getLatestSensorValue(RoutingContext routingContext) {
		int sensorId = Integer.parseInt(routingContext.request().getParam("sensorid"));
		int limit = Integer.parseInt(routingContext.request().getParam("limit"));
		mySqlClient.preparedQuery(
				"SELECT * FROM dad.sensorValues WHERE idSensor = ? ORDER BY `timestamp` DESC LIMIT ?;",
				Tuple.of(sensorId, limit), res -> {
					if (res.succeeded()) {
						// Get the result set
						RowSet<Row> resultSet = res.result();
                        System.out.println(resultSet.size());
                        List<SensorValue> result = new ArrayList<>();
                        for (Row elem : resultSet) {
                        	result.add(new SensorValue(elem.getInteger("idSensorValue"), elem.getFloat("value"), elem.getInteger("idSensor"),
									elem.getInteger("idSensor"), elem.getLong("timestamp"), elem.getBoolean("removed")));
                        }
                        routingContext.response()
                                .putHeader("content-type", "application/json; charset=utf-8")
                                .setStatusCode(200)
                                .end(gson.toJson(result));
                    } else {
                        System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al obtener los sensores: " + res.cause().getMessage());
                    }
				});
	}

	protected void  addSensorValue(RoutingContext routingContext) {
		Integer umbral = 28;
		final SensorValue sensorValue = gson.fromJson(routingContext.getBodyAsString(), SensorValue.class);
		mySqlClient.preparedQuery(
				"INSERT INTO dad.sensorValues (value, idGroup, idSensor, timestamp, removed) VALUES (?,?,?,?,?);",
				Tuple.of(sensorValue.getValue(), sensorValue.getidGroup() ,sensorValue.getIdSensor(), sensorValue.getTimestamp(),
						sensorValue.isRemoved()),
				res -> {
					if (res.succeeded()) {
						String topic =  sensorValue.getidGroup().toString();
						if(sensorValue.getValue() > umbral) {
							String payload = "ON";
							mqttClient.publish(topic, Buffer.buffer(payload), MqttQoS.AT_LEAST_ONCE, false, false);
						} else {
							String payload = "OFF";
							mqttClient.publish(topic, Buffer.buffer(payload), MqttQoS.AT_LEAST_ONCE, false, false);
						}
 	                    //String payload = gson.toJson(sensorValue);
						//mqttClient.publish(topic, Buffer.buffer(payload), MqttQoS.AT_LEAST_ONCE, false, false);
						long lastInsertId = res.result().property(MySQLClient.LAST_INSERTED_ID);
						sensorValue.setIdSensorValue((int) lastInsertId);
						routingContext.response().setStatusCode(201).putHeader("content-type",
                                "application/json; charset=utf-8").end("Valor de sensor añadido correctamente");
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al añadir el sensor: " + res.cause().getMessage());
					}
				});
	}
	
	protected void deleteSensorValue(RoutingContext routingContext) {
		int sensorValueId = Integer.parseInt(routingContext.request().getParam("sensorvalueid"));
		mySqlClient.preparedQuery("DELETE FROM dad.sensorValues WHERE idSensorValue = ?;",
				Tuple.of(sensorValueId), res -> {
					if (res.succeeded()) {
						if (res.result().rowCount() > 0) {
		                    routingContext.response()
		                        .setStatusCode(200)
		                        .putHeader("content-type", "application/json; charset=utf-8")
		                        .end(gson.toJson(new JsonObject().put("message", "Valor del sensor eliminado correctamente")));
		                } 
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
			            routingContext.response()
			                    .setStatusCode(500)
			                    .end("Error al conectar con la base de datos: ");
					}
				});
	}
	
//	private void deleteSensorValue(RoutingContext routingContext) {
//		int sensorValueId = Integer.parseInt(routingContext.request().getParam("sensorvalueid"));
//
//		DatabaseMessage databaseMessage = new DatabaseMessage(DatabaseMessageType.DELETE, DatabaseEntity.SensorValue,
//				DatabaseMethod.DeleteSensorValue, sensorValueId);
//
//		vertx.eventBus().request(RestEntityMessage.SensorValue.getAddress(), gson.toJson(databaseMessage), handler -> {
//			if (handler.succeeded()) {
//				DatabaseMessage responseMessage = deserializeDatabaseMessageFromMessageHandler(handler);
//				routingContext.response().putHeader("content-type", "application/json").setStatusCode(200)
//						.end(responseMessage.getResponseBody());
//			} else {
//				routingContext.response().putHeader("content-type", "application/json").setStatusCode(500).end();
//			}
//		});
//	}


	//ActuadorValues
	protected void  getLastActuatorStatus(RoutingContext routingContext) {
		int actuatorId = Integer.parseInt(routingContext.request().getParam("actuatorid"));
		mySqlClient.preparedQuery(
				"SELECT * FROM dad.actuatorStates WHERE idActuator = ? ORDER BY `timestamp` DESC LIMIT 1;",
				Tuple.of(actuatorId), res -> {
					if (res.succeeded()) {
						// Get the result set
						RowSet<Row> resultSet = res.result();
                        System.out.println(resultSet.size());
                        List<ActuatorStatus> result = new ArrayList<>();
                        for (Row elem : resultSet) {
                        	result.add(new ActuatorStatus(elem.getInteger("idActuatorState"),
									elem.getFloat("status"), elem.getBoolean("statusBinary"),
									elem.getInteger("idActuator"), elem.getInteger("idGroup"),elem.getLong("timestamp"),
									elem.getBoolean("removed")));
                        }
                        routingContext.response()
                                .putHeader("content-type", "application/json; charset=utf-8")
                                .setStatusCode(200)
                                .end(gson.toJson(result));
                    } else {
                        System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al obtener los estados de los actuadores: " + res.cause().getMessage());
                    }
				});
	}
	
	protected void addActuatorStatus(RoutingContext routingContext) {
		final ActuatorStatus actuatorState = gson.fromJson(routingContext.getBodyAsString(), ActuatorStatus.class);
		mySqlClient.preparedQuery(
				"INSERT INTO dad.actuatorStates (status, statusBinary, idActuator, idGroup, timestamp, removed) VALUES (?,?,?,?,?,?);",
				Tuple.of(actuatorState.getStatus(), actuatorState.isStatusBinary(), actuatorState.getIdActuator(), actuatorState.getIdGroup(),
						actuatorState.getTimestamp(), actuatorState.isRemoved()),
				res -> {
					if (res.succeeded()) {
						long lastInsertId = res.result().property(MySQLClient.LAST_INSERTED_ID);
						actuatorState.setIdActuatorState((int) lastInsertId);
						routingContext.response().setStatusCode(201).putHeader("content-type",
                                "application/json; charset=utf-8").end("Estado Actuador añadido correctamente");
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
                        routingContext.response().setStatusCode(500).end("Error al añadir el estado del actuador: " + res.cause().getMessage());
					}
				});
	}
	
	protected void deleteActuatorStatus(RoutingContext routingContext) {
		int idActuatorStatus = Integer.parseInt(routingContext.request().getParam("actuatorstatusid"));
		mySqlClient.preparedQuery("DELETE FROM dad.actuatorState WHERE idActuatorState = ?;",
				Tuple.of(idActuatorStatus), res -> {
					if (res.succeeded()) {
						if (res.result().rowCount() > 0) {
		                    routingContext.response()
		                        .setStatusCode(200)
		                        .putHeader("content-type", "application/json; charset=utf-8")
		                        .end(gson.toJson(new JsonObject().put("message", "Estado del actuador eliminado correctamente")));
		                } 
					} else {
						System.out.println("Error: " + res.cause().getLocalizedMessage());
			            routingContext.response()
			                    .setStatusCode(500)
			                    .end("Error al conectar con la base de datos: ");
					}
				});
	}
	


	@Override
	public void stop(Future<Void> stopFuture) throws Exception {
		super.stop(stopFuture);
	}

}
