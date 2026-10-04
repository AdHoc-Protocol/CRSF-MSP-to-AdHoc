## LinkStatistics

Packet sent to both the flight controller and handset with information about the RF link: RSSI, LQ, etc.

* uint8_t Uplink RSSI Ant. 1 ( dBm * -1 )
* uint8_t Uplink RSSI Ant. 2 ( dBm * -1 )
* uint8_t Uplink Package success rate / Link quality ( % )
  * Uplink LQ of 0 _may_ used to indicate a disconnected status to the handset
* int8_t Uplink SNR ( dB, or dB*4 for TBS I believe )
* uint8_t Diversity active antenna ( enum ant. 1 = 0, ant. 2 = 1 )
* uint8_t RF Mode ( 500Hz, 250Hz etc, varies based on ELRS Band or TBS )
* uint8_t Uplink TX Power ( enum 0mW = 0, 10mW, 25 mW, 100 mW, 500 mW, 1000 mW, 2000mW, 50mW )
* uint8_t Downlink RSSI ( dBm * -1 )
* uint8_t Downlink package success rate / Link quality ( % )
* int8_t Downlink SNR ( dB )

Uplink is the connection from the ground to the UAV and downlink is UAV to ground. Fields which are not known (e.g. downlink SNR going from RX to FC) should be set to 0.
