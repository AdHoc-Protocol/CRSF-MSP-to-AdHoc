### Device Info / device ping response (0x29)

The slave (transmitter module, receiver, flight controller, etc) replies to [[CRSF_FRAMETYPE_DEVICE_PING]] with an extended frame type `CRSF_FRAMETYPE_DEVICE_INFO` if the ping's destination matches the device's type. This contains the device display name, serial number, hardware version, software version, field count, and parameter protocol version.

* null-terminated string Display name
* uint32_t serial number - always "ELRS" for ExpressLRS
* uint32_t hardware version
* uint32_t software version
* uint8_t config parameter count
* uint8_t config parameter protocol version (0)

The Extended Source field should be used by the requesting device (which sent [[CRSF_FRAMETYPE_DEVICE_PING]]) to determine the type of client device responding, such as the transmitter module with address 0xEE. See [[CRSF Addresses]] for a full list.

```
TX: C8 1C 29 EA EE                                  к.)ко
    53 49 59 49 20 46 4D 33 30 00 45 4C 52 53 00 00 SIYI FM30.ELRS..
    00 00 00 00 00 00 13 00                         ........
    CA
C8 = sync
1C = len
29 = type
EA EE = extended packet dest/src (handset/txmodule)
53 49 59 49 20 46 4D 33 30 00 = display name, null terminated string
45 4C 52 53 = serial number, always "ELRS" for ExpressLRS
00 00 00 00 = hardware version
00 00 00 00 = software version, all 00 for ELRSv2, 00 MAJ MIN REV for ELRSv3
13 = number of config parameters = 19
00 = parameter protocol version
CA = CRC
```

### Quirks

* Display Name - ExpressLRS / EdgeTX limited to 15 characters
* Serial Number - ExpressLRS always sends `45 4C 52 53` for serial number, which is ASCII "ELRS".
* Software Version - ExpressLRS may send 00 or FF for MIN and REV fields if the device is not running a tagged release version.