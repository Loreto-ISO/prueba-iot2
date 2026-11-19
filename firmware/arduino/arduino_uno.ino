#include <Arduino.h>
#include <stdlib.h>
#include <string.h>

const uint8_t LED_PIN = 4;
const uint8_t BUZZER_PIN = 5;
const uint8_t BUTTON_PIN = 6;
const uint8_t TRIG_PIN = 7;
const uint8_t ECHO_PIN = 8;

const unsigned long SERIAL_BAUD = 9600;
const unsigned long SAMPLE_INTERVAL_MS = 500;
const unsigned long ECHO_TIMEOUT_US = 30000UL;
const unsigned int MAX_COMMAND_LENGTH = 32;
const float WARNING_DISTANCE_CM = 100.0f;

bool ledOn = false;
bool buzzerOn = false;
bool lastButtonReading = HIGH;
bool doorOpen = false;
unsigned long lastDebounceTime = 0;
unsigned long lastSampleTime = 0;
char commandBuffer[MAX_COMMAND_LENGTH + 1];
unsigned int commandLength = 0;
bool commandOverflow = false;

float measureDistance();
bool readDoorState();
void updateOutputs();
void sendTelemetry();
void processCommand(String command);
bool isValidCommand(String command);
void readSerialCommands();
void sendAck(const char *command, const char *status);

void setup() {
  pinMode(LED_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  digitalWrite(TRIG_PIN, LOW);
  digitalWrite(LED_PIN, LOW);
  digitalWrite(BUZZER_PIN, LOW);

  Serial.begin(SERIAL_BAUD);
  commandBuffer[0] = '\0';
  doorOpen = readDoorState();
}

void loop() {
  readSerialCommands();

  const unsigned long now = millis();
  if (now - lastSampleTime >= SAMPLE_INTERVAL_MS) {
    lastSampleTime = now;
    doorOpen = readDoorState();
    updateOutputs();
    sendTelemetry();
  }
}

float measureDistance() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  const unsigned long duration = pulseIn(ECHO_PIN, HIGH, ECHO_TIMEOUT_US);
  if (duration == 0) {
    return -1.0f;
  }

  return (duration * 0.0343f) / 2.0f;
}

bool readDoorState() {
  const bool reading = (digitalRead(BUTTON_PIN) == LOW);
  const unsigned long now = millis();

  if (reading != lastButtonReading) {
    lastDebounceTime = now;
    lastButtonReading = reading;
  }

  static bool stableButtonState = false;
  if (now - lastDebounceTime >= 40) {
    stableButtonState = reading;
  }

  return stableButtonState;
}

void updateOutputs() {
  digitalWrite(LED_PIN, ledOn ? HIGH : LOW);
  digitalWrite(BUZZER_PIN, buzzerOn ? HIGH : LOW);
}

void sendTelemetry() {
  const float distance = measureDistance();
  const char *door = doorOpen ? "open" : "closed";
  const char *state = "SAFE";

  if (doorOpen) {
    state = "ALERT";
  } else if (distance >= 0.0f && distance <= WARNING_DISTANCE_CM) {
    state = "WARNING";
  }

  Serial.print(F("{\"type\":\"telemetry\",\"distanceCm\":"));
  if (distance < 0.0f) {
    Serial.print(F("null"));
  } else {
    Serial.print(distance, 1);
  }
  Serial.print(F(",\"door\":\""));
  Serial.print(door);
  Serial.print(F("\",\"ledOn\":"));
  Serial.print(ledOn ? F("true") : F("false"));
  Serial.print(F(",\"buzzerOn\":"));
  Serial.print(buzzerOn ? F("true") : F("false"));
  Serial.print(F(",\"state\":\""));
  Serial.print(state);
  Serial.println(F("\"}"));
}

void processCommand(String command) {
  command.trim();
  command.toUpperCase();

  if (!isValidCommand(command)) {
    sendAck(command.c_str(), "REJECTED");
    return;
  }

  if (command == "LED_ON") {
    ledOn = true;
  } else if (command == "LED_OFF") {
    ledOn = false;
  } else if (command == "BUZZER_ON") {
    buzzerOn = true;
  } else if (command == "BUZZER_OFF") {
    buzzerOn = false;
  }

  updateOutputs();
  sendAck(command.c_str(), "ACCEPTED");
}

bool isValidCommand(String command) {
  command.trim();
  command.toUpperCase();
  return command == "LED_ON" ||
         command == "LED_OFF" ||
         command == "BUZZER_ON" ||
         command == "BUZZER_OFF";
}

void readSerialCommands() {
  while (Serial.available() > 0) {
    const char incoming = static_cast<char>(Serial.read());

    if (incoming == '\n') {
      if (!commandOverflow && commandLength > 0) {
        commandBuffer[commandLength] = '\0';
        processCommand(String(commandBuffer));
      } else if (commandOverflow) {
        sendAck("", "REJECTED");
      }

      commandLength = 0;
      commandOverflow = false;
      commandBuffer[0] = '\0';
      continue;
    }

    if (incoming == '\r') {
      continue;
    }

    if (incoming < 32 || incoming > 126 || commandLength >= MAX_COMMAND_LENGTH) {
      commandOverflow = true;
      continue;
    }

    if (!commandOverflow) {
      commandBuffer[commandLength++] = incoming;
    }
  }
}

void sendAck(const char *command, const char *status) {
  Serial.print(F("{\"type\":\"ack\",\"command\":\""));
  Serial.print(command);
  Serial.print(F("\",\"status\":\""));
  Serial.print(status);
  Serial.println(F("\"}"));
}
