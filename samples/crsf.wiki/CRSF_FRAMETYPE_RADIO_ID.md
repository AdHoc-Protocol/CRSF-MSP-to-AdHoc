Extended packet

* uint8_t subtype
* data

### subtypes

| Subtype (Hex / Decimal) | Enum | Description |
|--|--|--|
| 0x10 / 16 | `CRSF_FRAMETYPE_OPENTX_SYNC` | Mixer sync information from TX to handset |

#### CRSF_FRAMETYPE_OPENTX_SYNC

This frame type is sent from the transmitter module to the handset to allow the handset to sync its mixer calculation to the transmitter module's OTA timing. Also known as CRSFShot.

* int32_t packet interval in us * 10 (Big endian) - Request channels packets be sent this often.
  * e.g. 50Hz sent as 0x00030D40 / 200000
  * e.g. 333Hz sent as 0x0000754E / 30030
* int32_t phase shift correction - Request a phase adjustment to line up channels packet reception with the module's OTA timer.