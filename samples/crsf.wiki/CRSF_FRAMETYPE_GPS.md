### GPS Position (0x02)

Telemetry Item

* int32_t latitude in degrees * 1e7, big-endian
  * e.g. 28.0805804N sent as 0x10BCC1AC / 280805804
* int32_t longitude
* int16_t ground speed in km/h * 10, big-endian
  * e.g. 88 km/h sent as 0x0370 / 880
  * e.g. 15m/s sent as 0x021C / 540
* int16_t ground course / GPS heading in degrees * 100, big-endian
  * e.g. 90 degrees sent as 0x2328 / 9000
* uint16_t GPS altitude in meters + 1000m, big-endian
  * e.g. 0m sent as 0x03E8 / 1000
  * e.g. 10m sent as 0x03F2 / 1010
  * e.g. -10m sent as 0x03DE / 990
* uint8_t satellite count

Only use this packet for GPS position information-- use the [[CRSF_FRAMETYPE_BARO_ALTITUDE]] to report barometric altitude.