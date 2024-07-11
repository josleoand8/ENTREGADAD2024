#include <HTTPClient.h>
#include "ArduinoJson.h"
#include <NTPClient.h>
#include <WiFiUdp.h>
#include <PubSubClient.h>
#include <Adafruit_Sensor.h>
#include <pwmWrite.h>
#include <DHT.h>
#include <DHT_U.h>

int idSensor;
float actuador;


// Replace 0 by ID of this current device
const int DEVICE_ID = 124;
const int SENSOR_ID = 1369;

float valueSensor;


int test_delay = 1500; // so we don't spam the API
boolean describe_tests = true;

// Replace 0.0.0.0 by your server local IP (ipconfig [windows] or ifconfig [Linux o MacOS] gets IP assigned to your PC)




String serverName = "http://172.20.10.3:8080/";




HTTPClient http;

// Replace WifiName and WifiPassword by your WiFi credentials
#define STASSID "iPhone de Jose"    //"Your_Wifi_SSID"
#define STAPSK "josejr66" //"Your_Wifi_PASSWORD"





#define DHTTYPE DHT22
#define DHTPIN 2 // pin de placa, donde pone G2
#define LED_PIN 27 // pin de placa, donde pone G27
#define TEMPERATURE_THRESHOLD 25.0

// NTP (Net time protocol) settings
WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP);

// MQTT configuration
WiFiClient espClient;
PubSubClient client(espClient);

// Server IP, where de MQTT broker is deployed
const char *MQTT_BROKER_ADRESS = "172.20.10.3";
const uint16_t MQTT_PORT = 1883;

// Name for this MQTT client
const char *MQTT_CLIENT_NAME = "ArduinoClient_1";





// callback a ejecutar cuando se recibe un mensaje
// en este ejemplo, muestra por serial el mensaje recibido






String mensaje="";
void OnMqttReceived(char *topic, byte *payload, unsigned int length)
{
  Serial.print("Received on ");
  Serial.print(topic);
  Serial.print(": ");

  String content = "";

  for (size_t i = 0; i < length; i++)
  {
    content.concat((char)payload[i]);
    
  }

  if (content == "ON" || content == "OFF"){
  mensaje = content;

    if (mensaje == "ON") {
    // Activar el led
    digitalWrite(LED_PIN, HIGH);
    actuador=1;
    }else if(mensaje == "OFF"){
    actuador=0;
    digitalWrite(LED_PIN,LOW);  
    }

  }

//  StaticJsonDocument<200> doc;
//  DeserializationError error = deserializeJson(doc, content);
//  if(error) return;

//  valueSensor = doc["value"];

  Serial.print(content);
  Serial.println();
}

// inicia la comunicacion MQTT
// inicia establece el servidor y el callback al recibir un mensaje
void InitMqtt()
{
  client.setServer(MQTT_BROKER_ADRESS, MQTT_PORT);
  client.setCallback(OnMqttReceived);
}



DHT_Unified dht(DHTPIN, DHTTYPE);

uint32_t delayMS;
// Setup

unsigned long startTime;
void setup()
{
  Serial.begin(115200);
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(STASSID);
  pinMode(LED_PIN, OUTPUT);


  /* Explicitly set the ESP32 to be a WiFi-client, otherwise, it by default,
     would try to act as both a client and an access-point and could cause
     network-issues with your other WiFi-devices on your WiFi-network. */
  WiFi.mode(WIFI_STA);
  WiFi.begin(STASSID, STAPSK);

  while (WiFi.status() != WL_CONNECTED)
  {
    delay(500);
    Serial.print(".");
  }

  InitMqtt();

  Serial.println("");
  Serial.println("WiFi connected");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());
  Serial.println("Setup!");

  dht.begin();
  Serial.println(F("DHTxx Unified Sensor"));



  // Init and get the time
  timeClient.begin();

 


  // Declarar la variable global para almacenar el valor del sensor de temperatura



}

String response;

String serializeSensorValueBody(float value, int idGroup, int idSensor, long timestamp)
{
  // StaticJsonObject allocates memory on the stack, it can be
  // replaced by DynamicJsonDocument which allocates in the heap.
  
  //
  DynamicJsonDocument doc(2048);

  // Add values in the document
  //
  doc["idSensor"] = idSensor;
  doc["idGroup"] = idGroup;
  doc["timestamp"] = timestamp;
  doc["value"] = value;
  doc["removed"] = false;

  // Generate the minified JSON and send it to the Serial port.
  //
  String output;
  serializeJson(doc, output);
  Serial.println(output);

  return output;
}


String serializeActuatorStatusBody(float status, bool statusBinary, int idActuator, int idGroup, long timestamp)
{
  DynamicJsonDocument doc(2048);

  doc["status"] = status;
  doc["statusBinary"] = statusBinary;
  doc["idActuator"] = idActuator;
  doc["idGroup"] = idGroup;
  doc["timestamp"] = timestamp;
  doc["removed"] = false;

  String output;
  serializeJson(doc, output);
  return output;

}

String serializeDeviceBody(String deviceSerialId, String name, String mqttChannel, int idGroup)
{
  DynamicJsonDocument doc(2048);

  doc["deviceSerialId"] = deviceSerialId;
  doc["name"] = name;
  doc["mqttChannel"] = mqttChannel;
  doc["idGroup"] = idGroup;

  String output;
  serializeJson(doc, output);
  return output;
}



void deserializeActuatorStatusBody(String responseJson)
{
  if (responseJson != "")
  {
    DynamicJsonDocument doc(2048);

    // Deserialize the JSON document
    DeserializationError error = deserializeJson(doc, responseJson);

    // Test if parsing succeeds.
    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      return;
    }

    // Fetch values.
    int idActuatorState = doc["idActuatorState"];
    float status = doc["status"];
    bool statusBinary = doc["statusBinary"];
    int idActuator = doc["idActuator"];
    long timestamp = doc["timestamp"];

    Serial.println(("Actuator status deserialized: [idActuatorState: " + String(idActuatorState) + ", status: " + String(status) + ", statusBinary: " + String(statusBinary) + ", idActuator" + String(idActuator) + ", timestamp: " + String(timestamp) + "]").c_str());
  }
}

void deserializeDeviceBody(int httpResponseCode)
{

  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String responseJson = http.getString();
    DynamicJsonDocument doc(2048);

    DeserializationError error = deserializeJson(doc, responseJson);

    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      return;
    }

    int idDevice = doc["idDevice"];
    String deviceSerialId = doc["deviceSerialId"];
    String name = doc["name"];
    String mqttChannel = doc["mqttChannel"];
    int idGroup = doc["idGroup"];

    Serial.println(("Device deserialized: [idDevice: " + String(idDevice) + ", name: " + name + ", deviceSerialId: " + deviceSerialId + ", mqttChannel" + mqttChannel + ", idGroup: " + idGroup + "]").c_str());
  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }
}

void deserializeSensorsFromDevice(int httpResponseCode)
{

  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String responseJson = http.getString();
    
    DynamicJsonDocument doc(ESP.getMaxAllocHeap());

    // parse a JSON array
    DeserializationError error = deserializeJson(doc, responseJson);

    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      return;
    }

    // extract the values
    JsonArray array = doc.as<JsonArray>();
    for (JsonObject sensor : array)
    {
      int idSensor = sensor["idSensor"];
      String name = sensor["name"];
      String sensorType = sensor["sensorType"];
      int idDevice = sensor["idDevice"];

      

      Serial.println(("Sensor deserialized: [idSensor: " + String(idSensor) + ", name: " + name + ", sensorType: " + sensorType + ", idDevice: " + String(idDevice) + "]").c_str());
    }
  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }
}



void deserializeActuatorsFromDevice(int httpResponseCode)
{

  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String responseJson = http.getString();
    // allocate the memory for the document
    DynamicJsonDocument doc(ESP.getMaxAllocHeap());

    // parse a JSON array
    DeserializationError error = deserializeJson(doc, responseJson);

    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      return;
    }

    // extract the values
    JsonArray array = doc.as<JsonArray>();
    for (JsonObject sensor : array)
    {
      
      int idActuator = sensor["idActuator"];
      String name = sensor["name"];
      String actuatorType = sensor["actuatorType"];
      int idDevice = sensor["idDevice"];
      
      Serial.println(("Actuator deserialized: [idActuator: " + String(idActuator) + ", name: " + name + ", actuatorType: " + actuatorType + ", idDevice: " + String(idDevice) + "]").c_str());
  }
  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }


}


void deserializeSensorValuesFromSensor(int httpResponseCode)
{


  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String responseJson = http.getString();
    // allocate the memory for the document
    DynamicJsonDocument doc(ESP.getMaxAllocHeap());

    // parse a JSON array
    DeserializationError error = deserializeJson(doc, responseJson);

    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      
    }

    // extract the values
    JsonArray array = doc.as<JsonArray>();
    for (JsonObject sensor : array)
    {
      
      int idSensorValue = sensor["idSensorValue"];
      float value = sensor["value"];
      int idSensor = sensor["idSensor"];
      long timestamp = sensor["timestamp"];

     

      
      Serial.println(("Actuator deserialized: [idSensorValue: " + String(idSensorValue) + ", value: " + String(value) + ", timestamp: " + String(timestamp) + ", idSensor: " + String(idSensor) + "]").c_str());

  }

  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }

  
}

void test_response(int httpResponseCode)
{
  delay(test_delay);
  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String payload = http.getString();
    Serial.println(payload);
  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }
}

void describe(char *description)
{
  if (describe_tests)
    Serial.println(description);
}

void GET_tests()
{
  //describe("Test GET full device info");
  //String serverPath = serverName + "api/devices/" + String(DEVICE_ID);
  //http.begin(serverPath.c_str());
  //test_response(http.GET());
  //deserializeDeviceBody(http.GET());

  //describe("Test GET sensors from deviceID");
  //serverPath = serverName + "api/devices/" + String(DEVICE_ID) + "/sensors";
  //http.begin(serverPath.c_str());
  //deserializeSensorsFromDevice(http.GET());

  //describe("Test GET actuators from deviceID");
  //serverPath = serverName + "api/devices/" + String(DEVICE_ID) + "/actuators";
  //http.begin(serverPath.c_str());
  //deserializeActuatorsFromDevice(http.GET());

  //describe("Test GET sensors from deviceID and Type");
  //serverPath = serverName + "api/devices/" + String(DEVICE_ID) + "/sensors/Temperature";
  //http.begin(serverPath.c_str());
  //deserializeSensorsFromDevice(http.GET());

  //describe("Test GET actuators from deviceID");
  //serverPath = serverName + "api/devices/" + String(DEVICE_ID) + "/actuators/Buzzer";
  //http.begin(serverPath.c_str());
  //deserializeActuatorsFromDevice(http.GET());

  describe("Test GET sensorsValues from SensorID");
  String serverPath = serverName + "api/sensor_values/" + String(SENSOR_ID) + "/last";
  http.begin(serverPath.c_str());
  deserializeSensorValuesFromSensor(http.GET());
}

void POST_tests()
{
  sensors_event_t event;
  dht.temperature().getEvent(&event);
  float temperatura = event.temperature;

  String sensor_value_body = serializeSensorValueBody(temperatura,300,369,millis());
  describe("Test POST with sensor value");
  String serverPath = serverName + "api/sensor_values";
  http.begin(serverPath.c_str());
  test_response(http.POST(sensor_value_body));


  String actuator_states_body = serializeActuatorStatusBody(actuador,actuador,2,300,millis());
  describe("Test POST with actuator state");
  serverPath = serverName + "api/actuator_states";
  http.begin(serverPath.c_str());
  test_response(http.POST(actuator_states_body));



}


void ConnectMqtt()
{
  Serial.print("Starting MQTT connection...");
  if (client.connect(MQTT_CLIENT_NAME))
  {
    client.subscribe("300");                                    
    client.publish("300", "connected");
  }
  else
  {
    Serial.print("Failed MQTT connection, rc=");
    Serial.print(client.state());
    Serial.println(" try again in 5 seconds");

    delay(500);
  }
}

// gestiona la comunicación MQTT
// comprueba que el cliente está conectado
// no -> intenta reconectar
// si -> llama al MQTT loop
void HandleMqtt()
{
  if (!client.connected())
  {
    ConnectMqtt();
  }
  client.loop();
}



// Variables para controlar el tiempo transcurrido
// Run the tests!
// Run the tests!



int repeticiones;
void loop()
{

if(repeticiones == 1000){

  sensors_event_t event;
  dht.temperature().getEvent(&event);
  GET_tests();
  
  delay(test_delay);
  
  if (isnan(event.temperature)) {
    Serial.println(F("Error leyendo la temperatura!"));
  } else {
    POST_tests();
    Serial.print(F("Temperatura: "));
    Serial.print(event.temperature);
    Serial.println(F("°C"));
    Serial.print(mensaje);
    repeticiones = 0;
  }
    // Verificar si la temperatura supera el umbral predefinido

  
    
    
    
}else{
    repeticiones++;
}
//timeClient.update();
  HandleMqtt();
}

  
