#  CRSF-Enfinite Individual 

## Why

It might be more efficient to use key value pairs instead of compound sensors? 
 
## Frame Overview

The payload of a frame is a collection of Sensor Item objects, each having a pre-determined sensor ID (`eType`) and Value.

## eType Encoded Format

eType both defines what value the sensor represents (e.g. ESC 0 Voltage) as well as the type of data that follows. The lower two bits of the eType describe format of the Value that follows (`(eType << 2) | ValueType`). eTypes are encoded as varint. There are two types of data currently supported by this standard:

| Byte Lower 2 Bits | Type | Description |
|--|--|--|
| `00` | VARINT | Variable length integer. Encoded uint, int, bool, enum |
| `01` | LEN | Length of data chunk. Encoded string, byte arrays, subitems |
| `10` or `11` | None | Reserved for future use |

A parser is able to skip eTypes it does not understand how to parse by examining the eType field.

* Examine eType field's lower two bits
  * `00` (VARINT) - Read another varint, discard the value. The next byte following this value begins a new eType.
  * `01` (LEN) - Read another varint, skip the number of bytes specified by the varint. Do not include the LEN varint itself in this size. The next byte following this value begins a new eType.

When specifying eTypes, the eType number is independent of the lower two bits that will be used to decribe the data. For example Battery Cell Voltages is eType 2, but would be seen on the wire as `(2 << 2) | 01 = 9`.

## Strings

eTypes that contain string values use the LEN value to specify the size of the UTF-8 encoded string length that follows.

| Field | Bytes | Value | Description |
|--|--|--|--|
| eType | `0x25` | 37 | Model Name eType (0x09 + type LEN) |
| LEN | `0x0b` | 11 | 11 bytes follow
| Model Name | `0x47 0x65 0x6e 0x74 0x6c 0x65 0x20 0x4c 0x61 0x64 0x79` | Gentle Lady | 11 chars

## VARINT Lists

A list of values of the same sensor type may be shortened by omitting the eType on subsequent values. Only lists of VARINT are supported with this shorthand. To encode this, use LEN for the item type instead of VARINT and follow it with the length of all the included encoded values. For example, a list of 4x cell voltages could be expressed normally as:

| Field | Bytes | Value | Description |
|--|--|--|--|
| eType | `0x08` | 8 | Voltage Millivolts (type VARINT) |
| Cell Voltage 0 | `0xde 0x1c` | 3678 | Cell 3.678V |
| eType | `0x08` | 8 | Voltage Millivolts (type VARINT) |
| Cell Voltage 1 | `0xdc 0x1c` | 3676 | Cell 3.676V |
| eType | `0x08` | 8 | Voltage Millivolts (type VARINT) |
| Cell Voltage 2 | `0xd1 0x1c` | 3665 | Cell 3.665V |
| eType | `0x08` | 8 | Voltage Millivolts (type VARINT) |
| Cell Voltage 3 | `0xf9 0x1c` | 3705 | Cell 3.705V |

Instead a list of VARINTs can be used to reduce encoded length (12 bytes vs 10 bytes). 

| Field | Bytes | Value | Description |
|--|--|--|--|
| Voltage Millivolts eType | `0x05` | 5 | Voltage Millivolts begin (type LEN) |
| Voltage Millivolts Value | `0x08` | 8 | Size of all the cell voltage values |
| Cell Voltage 0 | `0xde 0x1c` | 3678 | Cell 3.678V |
| Cell Voltage 1 | `0xdc 0x1c` | 3676 | Cell 3.676V |
| Cell Voltage 2 | `0xd1 0x1c` | 3665 | Cell 3.665V |
| Cell Voltage 3 | `0xf9 0x1c` | 3705 | Cell 3.705V |

## Compound Sensors

In order to logically group sensors without allocating an eType for each instance of an object (e.g. multiple ESCs) a sensor value is allowed to have subitems.

### Example: Index (eType 0x00)

* Type: VARINT
* Description: An integer index to identify which instance this sensor is representing. Used in a subitem.

### Eample: Voltage Millivolts (eType 0x01)

* Type: VARINT / LEN
* Decription: A battery's single cell voltage in millivolts (0.001V).

### Example: Battery Voltage (eType 0x02)

* Type: LEN
* Description: Top level item for sending total battery or multiple cell voltages
* Subitems:
    * Index (0x00) - Optional. 0-based battery index of which instance this item represents. If not present in the subitem, battery 0 (first battery) is implied. Index **must** be the first value in the submessage.
    *  Voltage Millivolts (0x01) - (Repeats, Optional) Cell voltage. Repeat for each cell present. Fill only the optional fields to the last available value. i.e. do not fill the entire report with zeroes if the values are not available.
* Notes
    * All cells must be present in a single report. Do not split cell voltages across messages.
    * "No battery present" should be indicated as no cell voltages.
    * No total voltage is transmitted, the total voltage should be the sum of the cell voltages.
    * Implementations may choose to report total battery voltage using this compound sensor by reporting the total voltage in Cell Voltage 0. However, in that case they **must not** also publish cell voltages.

Example payload (cell voltage):

| Field | Bytes | Value | Description |
|--|--|--|--|
| eType | `0x09` | 9 | Battery voltage begin (type LEN) |
| LEN | `0x0c` | 12 | Length of subitems |
| Index eType | `0x00` | 0 | Index item begin (type VARINT) |
| Index Value | `0x00` | 0 | First battery instance |
| Voltage Millivolts eType | `0x05` | 5 | Voltage Millivolts begin (type LEN) |
| Voltage Millivolts Value | `0x08` | 8 | Size of all the cell voltage values |
| Cell Voltage 0 | `0xde 0x1c` | 3678 | Cell 3.678V |
| Cell Voltage 1 | `0xdc 0x1c` | 3676 | Cell 3.676V |
| Cell Voltage 2 | `0xd1 0x1c` | 3665 | Cell 3.665V |
| Cell Voltage 3 | `0xf9 0x1c` | 3705 | Cell 3.705V |

Example payload (total voltage):

| Field | Bytes | Value | Description |
|--|--|--|--|
| eType | `0x09` | 9 | Battery voltage begin (type LEN) |
| LEN | `0x03` | 3 | Length of subitems |
| Voltage Millivolts eType | `0x04` | 4 | Voltage Millivolts begin (type VARINT) |
| Total Voltage | `0x84 0x73` | 14724 | 14.724V |
