### Write Parameter (0x2D)

Write parameter is used to set a value or execute a command. Payload is `[field index] [value]` where value's length is dependent on the
field type. The encoded format of the data value is the same as it was received via [[CRSF_FRAMETYPE_PARAMETER_SETTINGS_ENTRY]].

Field indexes are not fixed. You can not simply set field 11 and expect it to be BIND.

* uint8_t field index
* uint8_t / uint16_t / uint32_t / string value. Size is dependent on the type of the parameter.

Note that ExpressLRS's implementation of the CRSF Config protocol assumes the client will reload most same-level parameters to account for any changes to other items caused by a Write Parameter. This includes the parent folder item if this item is a child, as well as any other value types (e.g. SELECT, UINT8, INFO). Sub-FOLDER type and COMMAND type parameters, as well as any child parameters of this item are not refreshed.

```
HOST: C8 06 2D EE EF 11 01 A5                         ..-îï..¥
C8 = sync
06 = len
2D = type
EE EF = extended packet dest/src (txmodule/elrslua)
11 = config field index (this is the Bind field index for this TX)
01 = value, step 1 since this field happens to be a COMMAND type
A5 = CRC
```

For the above command, the response is a parameter info (0x2B) update, indicating the value has changed to 02 with a string message "Binding..."
```
TX: C8 1A 2B EA EE                               ..+êî
11 00 00 0D 42 69 6E 64 00 02 C8 42 69 6E 64 69 ....Bind..ÈBindi
6E 67 2E 2E 2E 00                               ng....
66
```
