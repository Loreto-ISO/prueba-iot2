/*
 * ESP8266 WiFi/Firebase gateway for Hogar Seguro IoT.
 *
 * Arduino IDE libraries required:
 *   - Firebase ESP Client by Mobizt
 *   - ArduinoJson (installed as a Firebase dependency in most setups)
 *
 * Configure the following values for the deployment. Do not commit WiFi or
 * device-account credentials to a public repository. Use a dedicated Firebase
 * Authentication account for this device and restrictive RTDB rules.
 */
#include <Arduino.h>
#include <ArduinoJson.h>
#include <ESP8266WiFi.h>
#include <SoftwareSerial.h>
#include <Firebase_ESP_Client.h>
#include <time.h>

// SoftwareSerial wiring to Arduino UNO: ESP8266 RX <- UNO TX through a 5V-to-3.3V divider;
// ESP8266 TX -> UNO RX. Common GND is required. D5/D6 avoid ESP8266 boot-strap pins.
static const uint8_t UNO_RX_PIN = D5;
static const uint8_t UNO_TX_PIN = D6;
static const uint32_t UNO_BAUD = 9600;

static const char WIFI_SSID[] = "SET_WIFI_SSID";
static const char WIFI_PASSWORD[] = "SET_WIFI_PASSWORD";
static const char FIREBASE_API_KEY[] = "SET_FIREBASE_WEB_API_KEY";
static const char FIREBASE_DATABASE_URL[] = "https://SET_PROJECT-default-rtdb.firebaseio.com";
static const char FIREBASE_DEVICE_EMAIL[] = "SET_DEVICE_AUTH_EMAIL";
static const char FIREBASE_DEVICE_PASSWORD[] = "SET_DEVICE_AUTH_PASSWORD";
static const char DEVICE_ID[] = "casa-01";

static const unsigned long WIFI_RETRY_INTERVAL_MS = 10000UL;
static const unsigned long FIREBASE_RETRY_INTERVAL_MS = 5000UL;
static const unsigned long COMMAND_POLL_INTERVAL_MS = 1500UL;
static const unsigned long SERIAL_READ_TIMEOUT_MS = 1800UL;
static const unsigned long TELEMETRY_INTERVAL_MS = 1000UL;
static const size_t MAX_SERIAL_LINE = 240;
static const size_t MAX_COMMANDS_TO_SCAN = 20;

SoftwareSerial arduinoSerial(UNO_RX_PIN, UNO_TX_PIN);
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

unsigned long lastWiFiAttempt = 0;
unsigned long lastFirebaseAttempt = 0;
unsigned long lastCommandPoll = 0;
unsigned long lastTelemetryUpload = 0;
String serialLine;
bool serialOverflow = false;

bool connectWiFi();
void maintainWiFi();
bool connectFirebase();
void uploadDeviceState(const JsonDocument &telemetry);
void listenCommands();
bool sendCommandToArduino(const String &command, String &ackStatus);
bool readArduinoTelemetry(JsonDocument &document, unsigned long timeoutMs);
void readArduinoSerial();
void processArduinoLine(const String &line);
String commandFromType(const String &type, bool value);
void updateCommandStatus(const String &commandId, const String &status);
void setCommandProcessing(const String &commandId);
void reportGatewayState(bool wifiConnected, bool firebaseConnected);
String commandsPath();
String statePath();
String commandPath(const String &commandId);
uint64_t currentTimestampMs();

void setup() {
  Serial.begin(115200);
  arduinoSerial.begin(UNO_BAUD);
  arduinoSerial.setTimeout(SERIAL_READ_TIMEOUT_MS);
  serialLine.reserve(MAX_SERIAL_LINE);

  config.api_key = FIREBASE_API_KEY;
  config.database_url = FIREBASE_DATABASE_URL;
  auth.user.email = FIREBASE_DEVICE_EMAIL;
  auth.user.password = FIREBASE_DEVICE_PASSWORD;
  Firebase.reconnectWiFi(true);

  WiFi.persistent(false);
  WiFi.mode(WIFI_STA);
  connectWiFi();

  // Firebase Authentication handles secure TLS transport and refreshes the
  // device user's token through the Firebase client library.
  if (WiFi.status() == WL_CONNECTED) {
    connectFirebase();
  }

  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  reportGatewayState(WiFi.status() == WL_CONNECTED, Firebase.ready());
}

void loop() {
  maintainWiFi();
  readArduinoSerial();

  if (WiFi.status() == WL_CONNECTED && !Firebase.ready() &&
      millis() - lastFirebaseAttempt >= FIREBASE_RETRY_INTERVAL_MS) {
    connectFirebase();
  }

  if (WiFi.status() == WL_CONNECTED && Firebase.ready()) {
    if (millis() - lastCommandPoll >= COMMAND_POLL_INTERVAL_MS) {
      lastCommandPoll = millis();
      listenCommands();
    }

    if (millis() - lastTelemetryUpload >= TELEMETRY_INTERVAL_MS) {
      lastTelemetryUpload = millis();
      // Arduino reports telemetry asynchronously. Re-publish the most recently
      // received snapshot only when one is available and valid.
      static JsonDocument latestTelemetry;
      if (latestTelemetry.is<JsonObject>()) {
        uploadDeviceState(latestTelemetry);
      }
    }
  }

  delay(2);
}

bool connectWiFi() {
  if (WiFi.status() == WL_CONNECTED) return true;

  lastWiFiAttempt = millis();
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  const unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 12000UL) {
    readArduinoSerial();
    delay(100);
  }

  Serial.printf("WiFi connection: %s\n", WiFi.status() == WL_CONNECTED ? "connected" : "failed");
  reportGatewayState(WiFi.status() == WL_CONNECTED, Firebase.ready());
  return WiFi.status() == WL_CONNECTED;
}

void maintainWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;
  if (millis() - lastWiFiAttempt < WIFI_RETRY_INTERVAL_MS) return;

  lastWiFiAttempt = millis();
  WiFi.disconnect();
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.println(F("WiFi reconnect requested"));
  reportGatewayState(false, false);
}

bool connectFirebase() {
  lastFirebaseAttempt = millis();
  if (WiFi.status() != WL_CONNECTED) return false;
  if (String(FIREBASE_API_KEY) == "SET_FIREBASE_WEB_API_KEY" ||
      String(FIREBASE_DEVICE_EMAIL) == "SET_DEVICE_AUTH_EMAIL") {
    Serial.println(F("Configure Firebase API key, database URL, and device Authentication credentials."));
    return false;
  }

  Firebase.begin(&config, &auth);
  const unsigned long start = millis();
  while (!Firebase.ready() && millis() - start < 10000UL) {
    readArduinoSerial();
    delay(50);
  }

  Serial.printf("Firebase: %s\n", Firebase.ready() ? "ready" : "not ready");
  reportGatewayState(WiFi.status() == WL_CONNECTED, Firebase.ready());
  return Firebase.ready();
}

void readArduinoSerial() {
  while (arduinoSerial.available() > 0) {
    const char ch = static_cast<char>(arduinoSerial.read());
    if (ch == '\r') continue;

    if (ch == '\n') {
      if (!serialOverflow && serialLine.length() > 0) {
        processArduinoLine(serialLine);
      }
      serialLine = "";
      serialOverflow = false;
      continue;
    }

    if (serialLine.length() >= MAX_SERIAL_LINE) {
      serialOverflow = true;
      serialLine = "";
      continue;
    }
    if (!serialOverflow && ch >= 32 && ch <= 126) serialLine += ch;
  }
}

void processArduinoLine(const String &line) {
  StaticJsonDocument<384> document;
  const DeserializationError error = deserializeJson(document, line);
  if (error) {
    Serial.printf("Discarded malformed Arduino frame: %s\n", error.c_str());
    return;
  }

  const char *type = document["type"] | "";
  if (strcmp(type, "telemetry") == 0) {
    static JsonDocument latestTelemetry;
    latestTelemetry.clear();
    latestTelemetry.set(document.as<JsonVariantConst>());
    if (WiFi.status() == WL_CONNECTED && Firebase.ready()) {
      uploadDeviceState(latestTelemetry);
      lastTelemetryUpload = millis();
    }
    return;
  }

  if (strcmp(type, "ack") == 0) {
    const char *command = document["command"] | "";
    const char *status = document["status"] | "";
    Serial.printf("Arduino ACK: %s => %s\n", command, status);
  }
}

void uploadDeviceState(const JsonDocument &telemetry) {
  if (!Firebase.ready() || WiFi.status() != WL_CONNECTED) return;

  const char *door = telemetry["door"] | "";
  const bool doorOpen = strcmp(door, "open") == 0;
  const bool ledOn = telemetry["ledOn"] | false;
  const bool buzzerOn = telemetry["buzzerOn"] | false;
  const float distance = telemetry["distanceCm"] | -1.0f;

  FirebaseJson state;
  if (distance >= 0.0f) {
    state.set("distanceCm", distance);
  } else {
    state.set("distanceCm", nullptr);
  }
  state.set("doorOpen", doorOpen);
  state.set("ledOn", ledOn);
  state.set("buzzerOn", buzzerOn);
  state.set("wifiConnected", WiFi.status() == WL_CONNECTED);
  state.set("updatedAt", static_cast<double>(currentTimestampMs()));
  state.set("gatewayConnected", Firebase.ready());
  state.set("state", telemetry["state"] | "SAFE");

  if (!Firebase.RTDB.updateNode(&fbdo, statePath(), &state)) {
    Serial.printf("State upload failed: %s\n", fbdo.errorReason().c_str());
  }
}

void listenCommands() {
  if (!Firebase.ready()) return;

  QueryFilter query;
  query.orderByKey();
  query.limitToLast(MAX_COMMANDS_TO_SCAN);
  if (!Firebase.RTDB.getJSON(&fbdo, commandsPath(), &query)) {
    Serial.printf("Command read failed: %s\n", fbdo.errorReason().c_str());
    return;
  }

  String rawJson;
  FirebaseJson commandList = fbdo.to<FirebaseJson>();
  commandList.toString(rawJson, false);
  DynamicJsonDocument commands(8192);
  const DeserializationError parseError = deserializeJson(commands, rawJson);
  if (parseError || !commands.is<JsonObject>()) {
    if (rawJson != "{}" && rawJson.length() > 0) {
      Serial.printf("Command payload rejected: %s\n", parseError.c_str());
    }
    return;
  }

  for (JsonPair entry : commands.as<JsonObject>()) {
    JsonObject command = entry.value().as<JsonObject>();
    const String status = command["status"] | "";
    if (status != "PENDING") continue;

    const String commandId = entry.key().c_str();
    const String type = command["type"] | "";
    const String deviceId = command["deviceId"] | "";
    const bool value = command["value"] | false;
    const String issuedBy = command["issuedBy"] | "";
    const uint64_t createdAt = command["createdAt"] | static_cast<uint64_t>(0);

    if (deviceId != DEVICE_ID || issuedBy.length() == 0 || commandId.length() == 0 ||
        createdAt == 0 || currentTimestampMs() == 0 ||
        currentTimestampMs() > createdAt + 120000ULL) {
      updateCommandStatus(commandId, "REJECTED");
      continue;
    }

    const String serialCommand = commandFromType(type, value);
    if (serialCommand.length() == 0) {
      updateCommandStatus(commandId, "REJECTED");
      continue;
    }

    setCommandProcessing(commandId);
    String ackStatus;
    if (!sendCommandToArduino(serialCommand, ackStatus)) {
      updateCommandStatus(commandId, "FAILED");
    } else {
      updateCommandStatus(commandId, ackStatus == "ACCEPTED" ? "EXECUTED" : "REJECTED");
    }
    return; // Process one command per poll; retain FIFO-like key order.
  }
}

String commandFromType(const String &type, bool value) {
  if (type == "SET_LED") return value ? "LED_ON" : "LED_OFF";
  if (type == "SET_BUZZER") return value ? "BUZZER_ON" : "BUZZER_OFF";
  return String();
}

bool sendCommandToArduino(const String &command, String &ackStatus) {
  if (command != "LED_ON" && command != "LED_OFF" &&
      command != "BUZZER_ON" && command != "BUZZER_OFF") {
    return false;
  }

  while (arduinoSerial.available() > 0) arduinoSerial.read();
  arduinoSerial.println(command);

  const unsigned long startedAt = millis();
  while (millis() - startedAt < SERIAL_READ_TIMEOUT_MS) {
    while (arduinoSerial.available() > 0) {
      const char ch = static_cast<char>(arduinoSerial.read());
      if (ch == '\r') continue;
      if (ch == '\n') {
        StaticJsonDocument<256> ack;
        if (deserializeJson(ack, serialLine) == DeserializationError::Ok &&
            String(ack["type"] | "") == "ack" &&
            String(ack["command"] | "") == command) {
          ackStatus = ack["status"] | "";
          serialLine = "";
          return ackStatus == "ACCEPTED" || ackStatus == "REJECTED";
        }
        processArduinoLine(serialLine);
        serialLine = "";
      } else if (serialLine.length() < MAX_SERIAL_LINE) {
        serialLine += ch;
      } else {
        serialLine = "";
      }
    }
    maintainWiFi();
    delay(2);
  }

  Serial.println(F("Arduino command ACK timeout"));
  return false;
}

void setCommandProcessing(const String &commandId) {
  FirebaseJson patch;
  patch.set("status", "PROCESSING");
  patch.set("processingAt", static_cast<double>(currentTimestampMs()));
  if (!Firebase.RTDB.updateNode(&fbdo, commandPath(commandId), &patch)) {
    Serial.printf("Could not mark command processing: %s\n", fbdo.errorReason().c_str());
  }
}

void updateCommandStatus(const String &commandId, const String &status) {
  if (commandId.length() == 0 || !Firebase.ready()) return;
  FirebaseJson patch;
  patch.set("status", status);
  patch.set("completedAt", static_cast<double>(currentTimestampMs()));
  if (!Firebase.RTDB.updateNode(&fbdo, commandPath(commandId), &patch)) {
    Serial.printf("Command status update failed: %s\n", fbdo.errorReason().c_str());
  }
}

void reportGatewayState(bool wifiConnected, bool firebaseConnected) {
  if (!Firebase.ready()) return;
  FirebaseJson patch;
  patch.set("wifiConnected", wifiConnected);
  patch.set("gatewayConnected", firebaseConnected);
  patch.set("updatedAt", static_cast<double>(currentTimestampMs()));
  if (!Firebase.RTDB.updateNode(&fbdo, statePath(), &patch)) {
    Serial.printf("Gateway status update failed: %s\n", fbdo.errorReason().c_str());
  }
}

String commandsPath() {
  return String("/devices/") + DEVICE_ID + "/commands";
}

String statePath() {
  return String("/devices/") + DEVICE_ID + "/state";
}

String commandPath(const String &commandId) {
  return commandsPath() + "/" + commandId;
}

uint64_t currentTimestampMs() {
  const time_t now = time(nullptr);
  if (now < 1700000000) return 0;
  return static_cast<uint64_t>(now) * 1000ULL;
}
