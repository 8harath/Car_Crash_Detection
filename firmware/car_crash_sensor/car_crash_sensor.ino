/*
 * ESP32 Car Crash Detection - BLE Version
 * Corrected code for Android app compatibility
 * 
 * This code creates a BLE server that the Android app can connect to
 * and receive real-time sensor data for crash detection.
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>
#include <Wire.h>
#include <MPU6050.h>
#include <TinyGPS++.h>
#include <HardwareSerial.h>

// Pin definitions
#define MPU6050_SDA 21
#define MPU6050_SCL 22
#define GPS_RX 16
#define GPS_TX 17

// BLE Service and Characteristic UUIDs - MUST MATCH ANDROID APP
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// Objects
MPU6050 mpu;
TinyGPSPlus gps;
HardwareSerial GPSSerial(1);

// BLE components
BLEServer* pServer = nullptr;
BLECharacteristic* pCharacteristic = nullptr;
bool deviceConnected = false;

// Variables
float accelX, accelY, accelZ;
float impactForce = 0.0f;
float lastImpactForce = 0.0f;
unsigned long lastSendTime = 0;
const unsigned long SEND_INTERVAL = 100; // 100ms

// BLE Server callbacks
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        deviceConnected = true;
        Serial.println("BLE Client connected");
    }
    
    void onDisconnect(BLEServer* pServer) {
        deviceConnected = false;
        Serial.println("BLE Client disconnected");
        // Restart advertising to allow new connections
        pServer->startAdvertising();
        Serial.println("Restarted BLE advertising");
    }
};

void setup() {
    Serial.begin(115200);
    Serial.println("ESP32 Car Crash Detection - BLE Server Starting...");
    
    // Initialize I2C for MPU6050
    Wire.begin(MPU6050_SDA, MPU6050_SCL);
    
    // Initialize MPU6050
    mpu.initialize();
    if (!mpu.testConnection()) {
        Serial.println("MPU6050 connection failed");
    } else {
        Serial.println("MPU6050 initialized successfully");
    }
    
    // Initialize GPS
    GPSSerial.begin(9600, SERIAL_8N1, GPS_RX, GPS_TX);
    Serial.println("GPS initialized");
    
    // Initialize BLE
    BLEDevice::init("ESP32_CarCrash");
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    
    // Create BLE service
    BLEService* pService = pServer->createService(SERVICE_UUID);
    
    // Create BLE characteristic with read, write, and notify properties
    pCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_WRITE |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    
    // Start the service
    pService->start();
    
    // Start advertising
    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(false);
    pAdvertising->setMinPreferred(0x0);  // set value to 0x00 to not advertise this parameter
    BLEDevice::startAdvertising();
    
    Serial.println("BLE Server ready - waiting for connections...");
    Serial.println("Device name: ESP32_CarCrash");
    Serial.print("Service UUID: ");
    Serial.println(SERVICE_UUID);
    Serial.print("Characteristic UUID: ");
    Serial.println(CHARACTERISTIC_UUID);
}

void loop() {
    // Read accelerometer data
    readAccelerometer();
    
    // Calculate impact force
    calculateImpactForce();
    
    // Read GPS data
    readGPS();
    
    // Send data via BLE every 100ms if connected
    if (deviceConnected && (millis() - lastSendTime >= SEND_INTERVAL)) {
        sendDataViaBLE();
        lastSendTime = millis();
    }
    
    delay(10);
}

void readAccelerometer() {
    int16_t ax, ay, az;
    mpu.getAcceleration(&ax, &ay, &az);
    
    // Convert to g-force (MPU6050 sensitivity is 16384 LSB/g)
    accelX = ax / 16384.0f;
    accelY = ay / 16384.0f;
    accelZ = az / 16384.0f;
}

void calculateImpactForce() {
    // Calculate magnitude of acceleration
    float magnitude = sqrt(accelX*accelX + accelY*accelY + accelZ*accelZ);
    
    // Apply low-pass filter to reduce noise
    impactForce = 0.9f * lastImpactForce + 0.1f * magnitude;
    lastImpactForce = impactForce;
}

void readGPS() {
    while (GPSSerial.available()) {
        gps.encode(GPSSerial.read());
    }
}

void sendDataViaBLE() {
    if (!deviceConnected) {
        return;
    }
    
    // Format: "ACC:x,y,z|IMPACT:force|GPS:lat,lon"
    String data = "ACC:";
    data += String(accelX, 2) + ",";
    data += String(accelY, 2) + ",";
    data += String(accelZ, 2);
    data += "|IMPACT:";
    data += String(impactForce, 2);
    data += "|GPS:";
    
    if (gps.location.isValid()) {
        data += String(gps.location.lat(), 6) + ",";
        data += String(gps.location.lng(), 6);
    } else {
        data += "0,0";
    }
    
    // Send data via BLE characteristic
    pCharacteristic->setValue(data.c_str());
    pCharacteristic->notify();
    
    // Also print to Serial for debugging
    Serial.print("Sent via BLE: ");
    Serial.println(data);
    
    // Print connection status
    Serial.print("BLE Status: ");
    Serial.println(deviceConnected ? "Connected" : "Disconnected");
}

/*
 * Additional debugging functions
 */

void printSensorData() {
    Serial.println("=== Sensor Data ===");
    Serial.print("Accelerometer (g): X=");
    Serial.print(accelX, 2);
    Serial.print(" Y=");
    Serial.print(accelY, 2);
    Serial.print(" Z=");
    Serial.println(accelZ, 2);
    
    Serial.print("Impact Force: ");
    Serial.print(impactForce, 2);
    Serial.println("g");
    
    if (gps.location.isValid()) {
        Serial.print("GPS: Lat=");
        Serial.print(gps.location.lat(), 6);
        Serial.print(" Lon=");
        Serial.println(gps.location.lng(), 6);
    } else {
        Serial.println("GPS: No valid location");
    }
    
    Serial.print("BLE Connected: ");
    Serial.println(deviceConnected ? "Yes" : "No");
    Serial.println("==================");
}

/*
 * Test functions for development
 */

void testBLEConnection() {
    if (deviceConnected) {
        Serial.println("BLE connection test: Sending test message");
        String testMessage = "TEST:Hello from ESP32";
        pCharacteristic->setValue(testMessage.c_str());
        pCharacteristic->notify();
    } else {
        Serial.println("BLE connection test: Not connected");
    }
}

void testAccelerometer() {
    Serial.println("Accelerometer test:");
    for (int i = 0; i < 10; i++) {
        readAccelerometer();
        Serial.print("Sample ");
        Serial.print(i);
        Serial.print(": X=");
        Serial.print(accelX, 2);
        Serial.print(" Y=");
        Serial.print(accelY, 2);
        Serial.print(" Z=");
        Serial.println(accelZ, 2);
        delay(100);
    }
}
