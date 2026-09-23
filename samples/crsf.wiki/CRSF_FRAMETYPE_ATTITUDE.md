### UAS Attitude (0x1E)

Telemetry Item

* int16_t pitch angle in radians/10000
  * e.g. 180 degrees sent as 0x7AB7 / 31415
  * e.g. 45 degrees sent as 0x1EAD / 7853
  * e.g. -45 degrees sent as 0xE153 / -7853
* int16_t roll
* int16_t yaw

All values must be in the +/-180 degree +/-PI radian range.