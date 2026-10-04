### Read Parameter (0x2C)

For each parameter reported in the `CRSF_FRAMETYPE_DEVICE_INFO` packet, a `CRSF_FRAMETYPE_PARAMETER_READ` extended packet can be sent to read the parameter details. The order and index of these parameters is not fixed, i.e. you can not simply request field index 0x11 and expect it to be BIND.

* uint8_t field index - The first field index is 01, however there is also a field index 00 which is a folder-type parameter which lists the top level configuration items
* uint8_t chunk index - The first chunk index is 00. Splitting the configuration item across multiple packets requires it be split into chunks 

```
HOST: EE 06 2C EE EF 01 00 76
EE = dest
06 = len
2C = type
EE EF = extended packet dest/src
01 = config field index, this is requesting field 01
00 = chunk index, requesting chunk 00 of field 01
76 = CRC
```

