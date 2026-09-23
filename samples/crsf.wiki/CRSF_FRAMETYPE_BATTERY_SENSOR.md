### Battery Voltage and Current (0x08)

Telemetry Item

* int16_t voltage in dV (Big Endian)
  * e.g. 25.2V sent as 0x00FC / 252
  * e.g. 3.7V sent as 0x0025 / 37
  * e.g. 3276.7V sent as 0x7FFF / 32767
* int16_t current in dA (Big Endian)
  * e.g. 18.9A sent as 0x00BD / 189
  * e.g. 109.4A sent as 0x0446 / 1094
* int24_t used capacity in mAh
  * e.g. 2199mAh used sent as 0x0897 / 2199
* int8_t estimated battery remaining in percent (%)
  * e.g. 100% full battery sent as 0x64 / 100
  * e.g. 20% battery remaining sent as 0x14 / 20