## Packet Format
All packets are in the CRSF format `[sync] [len] [type] [payload] [crc8]` with a maximum total size of 64 bytes.

### SYNC

All serial CRSF packets begin with the CRSF SYNC byte `0xC8`, with the exception of EdgeTX's outgoing channel/telemetry packets, which begin with `0xEE`. Due to this, for compatibility, new code should support either for the SYNC byte, but all transmitted packets **MUST** begin with `0xC8`. I2C CRSF packets begin with a [[CRSF address | CRSF Addresses]].

### LEN

Length of bytes that follow, including type, payload, and CRC (PayloadLength+2). Overall packet length is PayloadLength+4 (sync, len, type, crc), or LEN+2 (sync, len).

### TYPE

CRSF_FRAMETYPE. See [[Packet Types]] Examples:
* CRSF_FRAMETYPE_LINK_STATISTICS = 0x14
* CRSF_FRAMETYPE_RC_CHANNELS_PACKED = 0x16
* CRSF_FRAMETYPE_DEVICE_PING = 0x28
* CRSF_FRAMETYPE_DEVICE_INFO = 0x29

### PAYLOAD

Data specific to the frame type. Maximum of 60 bytes.

### CRC

CRC8 using poly 0xD5, includes all bytes from type (buffer[2]) to end of payload.

## Extended Packet Format

`[sync] [len] [type] [[ext dest] [ext src] [payload]] [crc8]`

To support tunneling of packets through the CRSF protocol to remote endpoints, there is an extended packet format, which includes an extended dest and source as part of the payload (see [[CRSF Addresses]] for a list). In this case, the LEN field is actually PayloadLength+4, as the first two bytes are the extended dest and source. Maximum payload length is therefore 58 bytes for extended packets.

All packet types CRSF_FRAMETYPE 0x28 and higher use Extended Packet Format.
