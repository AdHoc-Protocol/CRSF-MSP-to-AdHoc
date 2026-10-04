### MSP DisplayPort Command (0x7D)

* uint8_t extended destination
* uint8_t extended source
* uint8_t subcommand ID
* Data

| SubCommand (Hex / Decimal) | Enum | Description
|--|--|--|
| 0x01 / 1 | `CRSF_DISPLAYPORT_SUBCMD_UPDATE` | Transmit DisplayPort buffer to Client |
| 0x02 / 2 | `CRSF_DISPLAYPORT_SUBCMD_CLEAR` | Request from Server: Clear client screen | 
| 0x03 / 3 | `CRSF_DISPLAYPORT_SUBCMD_OPEN` | Request from Client: Open CMS menu |
| 0x04 / 4 | `CRSF_DISPLAYPORT_SUBCMD_CLOSE` | Request from Client: Close CMS menu | 
| 0x05 / 5 | `CRSF_DISPLAYPORT_SUBCMD_POLL` | Request from Client: Poll/Refresh CMS menu |

### CRSF_DISPLAYPORT_SUBCMD_UPDATE

Data contains:

* uint8_t batch ID / flags
  * Flag 0x80 - First chunk
  * Flag 0x40 - Last chunk
* uint8_t index
* RLE encoded MSP DisplayPort data (50 bytes max)