### ExpressLRS Status (0x2E)

!! Non-Standard ExpressLRS Hacks !!

* uint8_t packetsBad - Bad packet (failed CRC) count in the past second
* uint16_t packetsGood - Good packet (passed CRC) count in the past second
* uint8_t flags - Higher values have higher priority
  * 0x00 - No flags
  * 0x01 - Connected
  * 0x04 - Model Mismatch
  * 0x08 - Armed
  * 0x20 - Not while connected (not used)
  * 0x40 - Baud rate too low (not used)
* string message - Display message

This message is sent my ExpressLRS transmitter modules in response to `CRSF_FRAMETYPE_PARAMETER_WRITE` of parameter 0x00. This is used to populate the bad/good packet counter at the top of the Lua, along with allowing sending warning (flash title bar) or error (popup message) messages.

