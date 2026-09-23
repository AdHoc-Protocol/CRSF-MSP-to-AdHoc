### Device Ping

The host (handset) sends an extended frame of type `CRSF_FRAMETYPE_DEVICE_PING` to discover the connected device and the number of config parameters available. If a CRSF device receives a device ping request with its device type in the extended destination (or broadcast), it **must** reply with a `CRSF_FRAMETYPE_DEVICE_INFO` even if it has no configurable parameters.

The host should expect a plurality of [[CRSF_FRAMETYPE_DEVICE_INFO]] responses depending on the number of currently connected devices, differentiated by their Extended Source fields.

```
HOST: C8 04 28 00 EA 54
C8 = sync
04 = len
28 = type
00 EA = extended packet
        00 = CRSF_ADDRESS_BROADCAST (extended destination)
        EA = CRSF_ADDRESS_RADIO_TRANSMITTER (extended source)
54 = CRC
```
