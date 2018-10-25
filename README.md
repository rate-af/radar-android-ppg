# RADAR Android PPG

Plugin to [RADAR pRMT Android](https://github.com/RADAR-base/radar-prmt-android) to measure PPG using the camera of a phone. This essentially takes preview snapshots of the camera when the left index finger is pressed against the camera. It then measures the amount of red, green and blue components. Later analysis can determine how this translates to blood volume pulse.

Include this plugin in a RADAR app by adding the following configuration to `build.gradle`:
```gradle
repositories {
    maven { url 'http://dl.bintray.com/radar-cns/org.radarcns' }
}

dependencies {
    implementation 'org.radarcns:radar-android-ppg:0.1.0'
}
```

and enabling `org.radarbase.passive.ppg.PhonePpgProvider` in the `device_services_to_connect` Firebase settings.

## Configuration

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| `phone_ppg_measurement_seconds` | int (s) | 60 | Number of seconds that a single measurement is supposed to take. |
| `phone_ppg_measurement_width` | int (px) | 200 | Preferred camera image width to analyze. Increasing this will make analysis slower. |
| `phone_ppg_measurement_height` | int (px) | 200 | Preferred camera image height to analyze. Increasing this will make analysis slower. |

This produces data to the following Kafka topics:

| Topic | Type |
| ----- | ---- |
| `android_phone_ppg` | `org.radarcns.passive.ppg.PhonePpg` |
