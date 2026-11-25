#include <SoftwareSerial.h>

// Pin 10 = RX Arduino (al TXD del HC-05) | Pin 11 = TX Arduino (al RXD del HC-05)
SoftwareSerial bluetooth(10, 11);

// --- PINES CONFIRMADOS ---
const int PIN_LED    = 3;
const int PIN_BOTON  = 4;
const int PIN_BUZZER = 6;
const int PIN_ECHO   = 12;
const int PIN_TRIG   = 13;

// --- CONFIGURACIÓN ---
const bool BUZZER_ACTIVO_EN_BAJO   = false; // true si suena cuando debería callar
const bool BOTON_SUELTO_ES_ALERTA  = false; // false = alarma solo al presionar el botón
const int DISTANCIA_ADVERTENCIA_CM = 150;
const unsigned long INTERVALO_BT_MS = 1000;

// --- VELOCIDAD DEL BUZZER (en milisegundos) ---
const unsigned long TIEMPO_PITIDO_MS   = 120; // Duración de cada pitido
const unsigned long TIEMPO_SILENCIO_MS = 120; // Pausa entre pitidos (baja este número para más rapidez)

unsigned long ultimoEnvioBT = 0;
unsigned long ultimoPulsoBuzzer = 0;
bool pulsoActivo = false;
String estadoAnterior = "";

void controlarBuzzer(bool encender) {
  if (BUZZER_ACTIVO_EN_BAJO) {
    digitalWrite(PIN_BUZZER, encender ? LOW : HIGH);
  } else {
    digitalWrite(PIN_BUZZER, encender ? HIGH : LOW);
  }
}

void sonarIntermitente(unsigned long ahora) {
  unsigned long intervalo = pulsoActivo ? TIEMPO_PITIDO_MS : TIEMPO_SILENCIO_MS;
  if (ahora - ultimoPulsoBuzzer >= intervalo) {
    ultimoPulsoBuzzer = ahora;
    pulsoActivo = !pulsoActivo;
    controlarBuzzer(pulsoActivo);
  }
}

void setup() {
  Serial.begin(9600);
  bluetooth.begin(9600);

  pinMode(PIN_LED, OUTPUT);
  pinMode(PIN_BOTON, INPUT_PULLUP);
  pinMode(PIN_BUZZER, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  pinMode(PIN_TRIG, OUTPUT);

  digitalWrite(PIN_LED, LOW);
  controlarBuzzer(false);
}

long medirDistanciaCm() {
  digitalWrite(PIN_TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(PIN_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_TRIG, LOW);

  long duracion = pulseIn(PIN_ECHO, HIGH, 30000);
  if (duracion == 0) return 999;
  return duracion * 0.034 / 2;
}

void loop() {
  unsigned long ahora = millis();

  // 1. Lectura de sensores
  int lecturaBoton = digitalRead(PIN_BOTON);
  bool puertaAbierta = BOTON_SUELTO_ES_ALERTA ? (lecturaBoton == HIGH) : (lecturaBoton == LOW);
  long distancia = medirDistanciaCm();
  String estadoActual = "";

  // 2. Lógica de los 3 estados
  if (puertaAbierta) {
    // ALERTA CRÍTICA: botón presionado
    estadoActual = "ALERTA_CRITICA";
    digitalWrite(PIN_LED, HIGH);
    sonarIntermitente(ahora);
  }
  else if (distancia > 0 && distancia <= DISTANCIA_ADVERTENCIA_CM) {
    // ADVERTENCIA: botón suelto + objeto <= distancia configurada
    estadoActual = "ADVERTENCIA";
    digitalWrite(PIN_LED, HIGH);
    pulsoActivo = false;
    controlarBuzzer(false);
  }
  else {
    // ESTADO SEGURO: botón suelto + sin objetos cerca
    estadoActual = "SEGURO";
    digitalWrite(PIN_LED, LOW);
    pulsoActivo = false;
    controlarBuzzer(false);
  }

  // 3. Envío por Bluetooth HC-05 y Monitor Serie
  if (estadoActual != estadoAnterior || (ahora - ultimoEnvioBT >= INTERVALO_BT_MS)) {
    ultimoEnvioBT = ahora;
    estadoAnterior = estadoActual;

    String mensajeJSON = "{\"estado\":\"" + estadoActual +
                         "\",\"puertaAbierta\":" + String(puertaAbierta ? "true" : "false") +
                         ",\"distanciaCm\":" + String(distancia) + "}";

    bluetooth.println(mensajeJSON);
    Serial.println(mensajeJSON);
  }

  delay(10);
}