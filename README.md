# RADAR Android PPG

Plugin to [RADAR pRMT Android](https://github.com/RADAR-base/radar-prmt-android) to measure PPG using the camera of a phone.

# Configuration

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| `phone_ppg_measurement_seconds` | int (s) | 60 | Number of seconds that a single measurement is supposed to take. |
| `phone_ppg_measurement_width` | int (px) | 200 | Preferred camera image width to analyze. Increasing this will make analysis slower. |
| `phone_ppg_measurement_height` | int (px) | 200 | Preferred camera image height to analyze. Increasing this will make analysis slower. |

This produces data to the following Kafka topics (all types are prefixed with the `org.radarcns.passive.phone.ppg` package).

| Topic | Type |
| ----- | ---- |
| `android_phone_ppg` | `PhonePpg` |
