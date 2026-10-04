### Parameter info (0x2B)

Response to 0x2C.
Payload is `[field index] [field chunks remaining] [parent] [type/hidden] [label] [value]`. All strings are ASCII and sent null-terminated (e.g. "Hi" is sent `48 69 00`)

* uint8_t field index 
* uint8_t chunks remaining after this one
* uint8_t parent - field index of the parent field if this item is a sub-item, or 0 if the item is at the top level
* uint8_t type/hidden
  * **low 7 bits**
  * 0x00 = UINT8 
  * 0x01 = INT8
  * 0x02 = UINT16
  * 0x03 = INT16
  * 0x04 - 0x07 = larger ints??
  * 0x08 = FLOAT (4 byte)
  * 0x09 = SELECT
  * 0x0A = STRING (null-terminated)
  * 0x0B = FOLDER
  * 0x0C = INFO (display string, not configurable)
  * 0x0D = COMMAND
  * 0x0F = VTX ?
  * high bit: hidden - If set, parameter is not to be displayed by the destination
* string label - Parameter name
* (variable length) value

### Value

For types 0x00 - 0x07, value contains multiple fields of the base type `T`, sent in big endian

  * `T` current value
  * `T` minimum value allowed
  * `T` maximum value allowed
  * string units

For 0x08 FLOAT type, value contains

  * int32_t current value
  * int32_t minimum value allowed
  * int32_t maximum value allowed
  * uint8_t precision - number of decimal places for display (e.g. current value 314 with precision 2 would display as 3.14)
  * uint32_t step used when adjusting value (e.g. 100 with precision 3 would be 0.100 per step)
  * string units

For 0x09 SELECT (drop down selection) type, value contains

  * uint8_t current selected value index
  * string options - list of mutually-exclusive selectable options, separated with semicolons `';'` (e.g. `A;B;C` represents three options, A, B, or C). Semicolons in data are not allowed and must be removed by the sender. Blank items are allowed and should not be displayed by the receiver, although their indexes must remain coherent.
  * string units

For 0x0A STRING (editable string) type, value contains
 
  * string current value
  * uint8_t maximum string length allowed (optional)

For 0x0B FOLDER (item containing sub items) type

  * string display name - Sub items should only have folder type items for their parent

Fox 0x0C INFO (display non-editable static string)

  * string display

For 0x0D COMMAND (execute command / status) type

  * uint8_t step - ExpressLRS values, TBS values unknown
    * 0 - IDLE
    * 1 - CLICK - user has clicked the command to execute
    * 2 - EXECUTING - command is executing
    * 3 - ASKCONFIRM - command pending user OK
    * 4 - CONFIRMED - user has clicked confirm
    * 5 - CANCEL - user has requested cancel
    * 6 - QUERY - host is requested updated status
  * uint8_t timeout - In units of 10ms ticks, e.g. 200 = 2000ms = 2s. The exact purpose of this field is unclear. ExpressLRS uses this as the polling interval for refreshing the command status.
  * string info / status


### Example SELECT type message

```
TX: C8 21 2B EA EE                               к!+ко
03 00 00 09 42 54 20 54 65 6C 65 6D 65 74 72 79 ....BT Telemetry
00 4F 66 66 3B 4F 6E 00 00 00 01 00 00          .Off;On......
4C
C8 = sync
21 = len
2B = type
EA EE = extended packet dest/src (handset/txmodule)
03 = config field index, parameter field 03
00 = chunks remaining, 0 chunks remaining after this one
00 = parent, this field is parented under folder (config index) 00
09 = field type
42 54 20 54 65 6C 65 6D 65 74 72 79 00  = field label "BT Telemetry"
4F 66 66 3B 4F 6E 00 = SELECT options "Off;On"
00 = field value = 0
00 = minimum allowed value = 0
01 = maximum allowed value = 1
00 = default value = 0
00 = units (null-terminated string)
4C = CRC
```

