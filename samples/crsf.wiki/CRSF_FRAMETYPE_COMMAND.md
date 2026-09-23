## Command

Commands are sent using extended `CRSF_FRAMETYPE_COMMAND` (0x32) type packets. The variable-length payload `[realm] [cmd payload] [crc8-ba]` includes a single byte command type/id/realm, and any data needed for the command, followed by a second CRC. The additional `crc8-ba` is CRC8 using poly 0xBA and includes all bytes from type (buffer[2] = `CRSF_FRAMETYPE_COMMAND`), including the extended header, to the end of payload.

#### Extended payload

* uint8_t command_realm
* uint8_t subcommand / cmd_payload[]
* uint8_t crc8-ba. NOTE: Many existing implementations omit this field on some packets. Transitional parsers **SHOULD** allow command packets without this second CRC to pass as if they do and the CRC was correct.

Typically command realm is followed by the subcommand making the extended payload `[realm] [subcmd] [payload] [crc8-ba]`.

## Command Realms

| Realm (Hex / Decimal) | Enum | Description |
|--|--|--|
| 0x10 / 16 | `CRSF_COMMAND_SUBCMD_RX` | Receiver |
| 0x0A / 10 | `CRSF_COMMAND_SUBCMD_GENERAL` | General |

### CRSF_COMMAND_SUBCMD_RX

| Command (Hex / Decimal) | Enum | Description |
|--|--|--|
| 0x01 / 1 | `CRSF_COMMAND_SUBCMD_RX_BIND` | Enter binding mode |
| 0x05 / 5 | `COMMAND_MODEL_SELECT_ID` | Set Receiver / Model ID |

#### CRSF_COMMAND_SUBCMD_RX_BIND

Requests the target device enter bind mode / send a bind information / complete binding. This is the command sent by the Betaflight Configurator "Bind RX" button for UART CRSF receivers.

* ExpressLRS - (>=3.4.0) A packet with extended destination `CRSF_ADDRESS_CRSF_RECEIVER` received by the receiver will put the receiver into binding mode. Destination of `CRSF_ADDRESS_CRSF_TRANSMITTER` received by the transmitter module will broadcast the binding phrase to complete binding.
* TBS - ??

#### COMMAND_MODEL_SELECT_ID

The ReceiverID is stored on the handset and passed to the transmitter module when a connection to it is established, which **must be** prior to the module establishing a connection to the receiver. This is done using an extended CRSF_FRAMETYPE_COMMAND (0x32) type packet with command realm RX (0x10) and command COMMAND_MODEL_SELECT_ID (0x05).

Example: Set ReceiverID to 54 (0x36) `c8 08 32 ee ea 10 05 36 26 a0`.

### CRSF_COMMAND_SUBCMD_GENERAL

| Command (Hex / Decimal) | Enum | Description |
|--|--|--|
| 0x70 / 112 | `CRSF_COMMAND_SUBCMD_GENERAL_CRSF_SPEED_PROPOSAL` | (CRSFv3) Proposed new CRSF port speed |
| 0x71 / 113 | `CRSF_COMMAND_SUBCMD_GENERAL_CRSF_SPEED_RESPONSE` | (CRSFv3) response to the proposed CRSF port speed |

#### CRSF_COMMAND_SUBCMD_GENERAL_CRSF_SPEED_PROPOSAL

* uint8_t port id - ID of this request, used for response
* uint32_t proposed baud rate (Big Endian)
  * e.g. 416666bps sent as 0x00065B9A / 416666
  * e.g. 5.25Mbps sent as 0x00501BD0 / 5250000

#### CRSF_COMMAND_SUBCMD_GENERAL_CRSF_SPEED_RESPONSE

* uint8_t port id - port ID sent in `CRSF_COMMAND_SUBCMD_GENERAL_CRSF_SPEED_PROPOSAL` command
* uint8_t accept - 0 if the new baud rate is not acceptable, 1 if the baud rate is accepted and baud rate will change shortly
