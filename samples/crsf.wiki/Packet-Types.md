| Type (Hex / Decimal) | Extended | Telemetry | Enum | Description |
|--|--|--|--|--|
| 0x02 / 2 | | Y | [[CRSF_FRAMETYPE_GPS]] | GPS position, ground speed, heading, altitude, satellite count |
| 0x07 / 7 | | | [[CRSF_FRAMETYPE_VARIO]] | Vertical speed |
| 0x08 / 8 | | Y | [[CRSF_FRAMETYPE_BATTERY_SENSOR]] | Battery voltage, current, mAh, remaining percent |
| 0x09 / 9 | | Y | [[CRSF_FRAMETYPE_BARO_ALTITUDE]] | Barometric altitude, vertical speed (optional) |
| 0x0B / 11 | | | [[CRSF_FRAMETYPE_HEARTBEAT]] | (CRSFv3) Heartbeat |
| 0x14 / 20 | | | [[CRSF_FRAMETYPE_LINK_STATISTICS]] | Signal information. Uplink/Downlink RSSI, SNR, Link Quality (LQ), RF mode, transmit power |
| 0x16 / 22 | | | [[CRSF_FRAMETYPE_RC_CHANNELS_PACKED]] | Channels data (both handset to TX and RX to flight controller) |
| 0x17 / 23 | | | [[CRSF_FRAMETYPE_SUBSET_RC_CHANNELS_PACKED]] | (CRSFv3) Channels subset data |
| 0x1C / 28 | | Y | [[CRSF_FRAMETYPE_LINK_RX_ID]] | Receiver RSSI percent, power? |
| 0x1D / 29 | | Y | [[CRSF_FRAMETYPE_LINK_TX_ID]] | Transmitter RSSI percent, power, fps? |
| 0x1E / 30 | | Y | [[CRSF_FRAMETYPE_ATTITUDE]] | Attitude: pitch, roll, yaw |
| 0x21 / 33 | | Y | [[CRSF_FRAMETYPE_FLIGHT_MODE]] | Flight controller flight mode string |
| 0x28 / 40 | Y | | [[CRSF_FRAMETYPE_DEVICE_PING]] | Sender requesting DEVICE_INFO from all destination devices |
| 0x29 / 41 | Y | | [[CRSF_FRAMETYPE_DEVICE_INFO]] | Device name, firmware version, hardware version, serial number (PING response) | 
| 0x2B / 43 | Y | | [[CRSF_FRAMETYPE_PARAMETER_SETTINGS_ENTRY]] | Configuration item data chunk |
| 0x2C / 44 | Y | | [[CRSF_FRAMETYPE_PARAMETER_READ]] | Configuration item read request |
| 0x2D / 45 | Y | | [[CRSF_FRAMETYPE_PARAMETER_WRITE]] | Configuration item write request | 
| 0x2E / 46 | Y | | [[CRSF_FRAMETYPE_ELRS_STATUS]] | !!Non Standard!! ExpressLRS good/bad packet count, status flags |
| 0x32 / 50 | Y | | [[CRSF_FRAMETYPE_COMMAND]] | CRSF command execute | 
| 0x3A / 58 | Y | | [[CRSF_FRAMETYPE_RADIO_ID]] | Extended type used for OPENTX_SYNC | 
| 0x78 / 120 | Y | | `CRSF_FRAMETYPE_KISS_REQ` | KISS request |
| 0x79 / 121 | Y | | `CRSF_FRAMETYPE_KISS_RESP` | KISS response |
| 0x7A / 122 | Y | | [[CRSF_FRAMETYPE_MSP_REQ]] | MSP parameter request / command |
| 0x7B / 123 | Y | | `CRSF_FRAMETYPE_MSP_RESP` | MSP parameter response chunk |
| 0x7C / 124 | Y | | `CRSF_FRAMETYPE_MSP_WRITE` | MSP parameter write |
| 0x7D / 125 | Y | | [[CRSF_FRAMETYPE_DISPLAYPORT_CMD]] | (CRSFv3) MSP DisplayPort control command |
| 0x80 / 128 | Y | | `CRSF_FRAMETYPE_ARDUPILOT_RESP` | Ardupilot output? | 
