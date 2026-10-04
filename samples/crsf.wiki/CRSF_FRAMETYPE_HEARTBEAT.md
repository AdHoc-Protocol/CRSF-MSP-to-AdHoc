### Heartbeat (0x0B)

CRSFv3

* uint16_t Origin Device Address (Big Endian)
  * e.g. Flight Controller is online sends `CRSF_ADDRESS_FLIGHT_CONTROLLER` 0xC8 / 200

Heartbeat message must always be sent even if telemetry is disabled, which allows the other side of the connection to detect baud rate mismatches.

TODO: Recommended interval? Proposed: Something should be transmitted at least every 1s? Transmit every 500ms if UART is idle?