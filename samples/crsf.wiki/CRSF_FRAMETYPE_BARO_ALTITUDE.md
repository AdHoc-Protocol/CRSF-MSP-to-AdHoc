### Barometric Altitude

Telemetry Item

* uint16_t altitude
  * If the high bit is not set, altitude value is in decimeters + 10000, allowing altitudes of -1000.0m to 2276.7m
    * 10m sent as (100 + 10000) = 0x2774 / 10100
    * -10m sent as (-100 + 10000) = 0x26AC / 9900
  * If the high bit is set, altitude value is in meters, allowing altitudes of 0m - 32767m
    * 10m sent as (10 | 0x8000) = 0x800A / 32768
* int16_t vertical speed (optional) in cm/s (e.g. 1.5m/s sent as 150)

Use this telemetry type for barometer-based variometers to include altitude and vertical speed in a single packet. GPS-based variometers should use the [[CRSF_FRAMETYPE_GPS]] and [[CRSF_FRAMETYPE_VARIO]] packets separately.

Vertical speed is listed as optional. This means if the payload is only 2 bytes, it just contains the altitude. If the payload is 4 bytes, it also includes the vertical speed.