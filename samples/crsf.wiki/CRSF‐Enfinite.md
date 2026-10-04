#  CRSF-Enfinite Telemetry frame (CRSF-E)

_Other name ideas: Endless, Episodic_ 

## Purpose

CRSF defines a handful of telemetry frame definitions to provide basic information from the model to the handset. Each sensor has its own dedicated frame type. However, there are a large number of unsupported sensors which are not represented by these frame types. This proposed frame type seeks to provide a framework for extending support to these sensors in a manner which does not consume more CRSF frame types than necessary, as there are only 28 remaining non-extended CRSF frame IDs remaining as of this writing.

## Frame Overview 

* Type: Hex `0x1b` / Dec `27`
* Size: 2-60 bytes

The payload of a CRSF-E telemetry frame is defined as one or more CRSF-E compound sensors, each having a pre-determined sensor ID (`eType`), length, and payload. A compound sensor can not span multiple frames-- each CRSF-E frame must be entirely self-contained.

### Compound Sensor

A compound sensor is a collection of related sensor values, and is identical to the current system of CRSF telemetry items. For example, a `BATTERY_SENSOR` compound sensor would be comprised of battery voltage, current, mAh used capacity, and percentage. Individual sensors should be packed into groups for bandwidth efficiency and coherency of values (i.e they were all collected at the same time). Sensor IDs (`eTypes`) are allocated by the crsf-wg and use fixed structure messages. The length is included in every compound sensor to allow older parsers to skip eTypes they do not know how to parse.

There is no guarantee of delivery for any sensor that is transmitted as well as no guarantee that the sensors will be arrive in the order they were sent. 

#### Optional Fields 

Compound sensors may contain optional fields in their message definition. When transmitting the sensor, the report must include all optional values up to the last used value. Example (ESC Sensor):

| Field | Value | Required |
|--|--|--|
| eType | 0x08 | * |
| Length | 0x07 | * |
| ESC Index | 0x00 | * |
| RPM | None | * |
| Temperature | None | * |
| Voltage | 24.63V | * |
| Current | 39.4A | * |
| Motor Temperature | None |  |
| ESC Status | None | |

### Sensor Item

A sensor item is the smallest unit of telemetry, representing a single value, such a voltage, temperature, or speed.

#### Example Frame

**NOTE**: Example uses ficticious eTypes as no official compound sensors are defined yet 

* `0xc8 0x12 0x1b 0x09 0x04 0xf4 0x4e 0xac 0x02 0x08 0x08 0x00 0xd9 0x13 0xeb 0x0b 0x97 0x11 0x14 crc`
  * Sync byte: `0xc8`
  * Length: `0x12` / `18`
  * Type: `0x1b` / `27`
  * Payload: `0x09 0x04 0xf4 0x4e 0xac 0x02 0x08 0x08 0x00 0xd9 0x13 0xeb 0x0b 0x97 0x11 0x14`
  * CRC: `crc`

|Name|Bytes|Value|Description|
|--|--|--|--|
| eType | `0x09` | 9 (`BARO_ALT`) | `BARO_ALT` compound sensor begin |
| Len | `0x04` | 4 | Sensor contains 4 bytes |
| Altitude | `0xf4 0x4e` | 10100 | Altitude +10.0m |
| VSpd | `0xac 0x02` | 300 | VSpd +1.5 m/s |
| eType | `0x08` | 8 (`BATTERY_SENSOR`) | `BATTERY_SENSOR` compound sensor begin |
| Length | `0x08` | 8 | 8 bytes follow |
| Battery Index | `0x00` | 0 | First battery |
| Total Voltage | `0xd9 0x13` | 2521 | 25.21V |
| Total Current | `0xeb 0x0b` | 1899 | 18.99A |
| Used capacity | `0x97 0x11` | 2199 | 2199mAh used  |
| Remaining % | `0x14` | 20 | 20% remaining |

## Sensor Item Data Types

CRSF-E compound sensors are composed of sensor item values of integer or string data types. All sensor values must transform their values to be represented by one of these types, and consideration should also be made to encode values to produce the smallest encoded value. No float types are supported; float values must be transformed to integers via decimal shifting or a multiplier.

* **UINT**: Unsigned integers 8bit to 32bit
* **INT**: Signed integer 8bit to 32bit
* **STRING**: Strings of UTF-8 characters

## Value Encoding - Base 128 Varint

Variable length integers, or _varints_ are at the core of the wire format. They allow encoding unsigned integers using anywhere between one and five bytes, with small values using fewer bytes.

Each byte in the varint has a _continuation bit_ that indicates if the byte that follows it is part of the varint. This is the most significant bit (MSB) of the byte. The lower 7 bits are the payload; the resulting integer is built by appending together the 7-bit payloads of its constituent bytes.

For example, here is the number 1 encoded as `1` - it is a single byte so the MSB is not set:

```c
0000 0001
^ msb
```

And here is 300, encoded as `0xac 0x02`

```c
1010 1100  0000 0010
^ msb      ^ msb
```

To convert from the encoded value to the original integer, drop the MSB from each byte, as this is just there to delineate where the value ends. The 7-bit payloads are then in little-endian format, convert endianness as needed, concatenate them, and interpret the value as an unsigned integer.

```c
10101100 00000010  // original input
 0101100  0000010  // drop continuation bits
 0000010  0101100  // swap to big endian
   00000100101100  // contatenate
 256 + 32 + 8 + 4 = 300  // bits to unsigned integer
```

| Encoded Bytes | Lowest Value | Highest Value |
|--|--|--|
| 1 | 0 | 127 | 
| 2 | 128 | 16383 |
| 3 | 16384 | 2097151 |
| 4 | 2097152 | 268435455 |
| 5 | 268435456 | 4294967295 |

## Varint to Other Types

**All wire-encoded values are unsigned integers** and these unsigned integers can then be converted to other types.

### More Integer Types

Bools and Enums are encoded as if they were unsigned integers. Bools are `0` for false or `1` for true.

### Signed Integers (`INT`)

Signed integers are encoded using ZigZag encoding to preseve space. Encoding the value -1 using a traditional two's complement varint would consume the full 5 bytes, but in ZigZag encoding -1 is still just 1 byte. To ZigZag encode, positive integers are encoded as `2 * p` (always even) and negative integers are encoded as `2 * |p| - 1`. The encoding thus "zig-zags" between positive and negative numbers. For example:

| Signed Original | Encoded As |
|--|--|
|0|0|
|-1|1|
|1|2|
|-2|3|

## String Type (`STRING`)

A string is a Varint encoded length, followed by UTF-8 character data, encoded as a sequence of varints.

## Compound Sensor Formal Specification

Compound sensors may be submitted to the crsf-wg for approval and inclusion in the formal CRSF-E dictionary. An approved compound sensor will be assigned a permanent ID referred to here as the `eType`. Care should be taken to define the sensor as broadly as possible to support the most generalized usage and the largest collection of sensor items rather than simply requesting a new eType for individual values.

Sensor definitions **must** contain at least 4 sections

1. Purpose: Reason this compound sensor exists / what data is serves to report
2. Message format: A table containing Field, Type, and Description
  * Field: Name of field. Must start with a row for eType 
  * Type: Data type of field, `UINT`, `INT`, or `STRING`
  * Description: Information about what the data is. If additional transformation of the value is required, such as scaling or shifting the value, the description **must** include this information as part of the specification. Do not include examples here.
3. Example payload: An example hexdecimal payload followed by table containing Field, Bytes, Value, Description. In the case of a payload containing strings, the example may be presented in canonical format (aka `hexdump -C`). Multiple examples may be included.
  * Field: Same name as field from the Message Format section
  * Bytes: Hexadecimal bytes from the full payload
  * Value: Varint decoded value of the hexadecimal bytes, not transformed
  * Description: Transformed final value as the user would expect to see it, along with additional information specific to that value.
4. Notes: Notes regarding representing interesting values, or best practices.

crsf-wg will attenmt to reserve eTypes below 128 (single byte eTypes) for use with compound sensors that are expected to be used more broadly.

### Example: Battery Cell Voltages (eType 0x00)

**Purpose**: Total battery voltage or individual cell voltages for one or more batteries. Not present in CRSF.

| Field | Type | Description |
|--|--|--|
| eType | `UINT` | `0x00` / `0` |
| Length | `UINT` | Total encoded length of all data in this message |
| Battery Index | `UINT` | 0-based battery index if multiple batteries are present |
| Cell Voltages | `UINT[]` | Optional. Remainder of message is a list of encoded cell voltages in millivolts (0.001V) |

Example payload: `0x00 0x09 0x00 0xde 0x1c 0xdc 0x1c 0xd1 0x1c 0xf9 0x1c` 

| Field | Bytes | Value | Description |
|--|--|--|--|
| eType | `0x00` | 0 | `BATTERY_CELLS` compound sensor begin |
| Length | `0x09` | 9 | 9 bytes follow |
| Battery Index | `0x00` | 0 | Battery index 0 (first battery) |
| Cell Voltage 0 | `0xde 0x1c` | 3678 | Cell 3.678V |
| Cell Voltage 1 | `0xdc 0x1c` | 3676 | Cell 3.676V |
| Cell Voltage 2 | `0xd1 0x1c` | 3665 | Cell 3.665V |
| Cell Voltage 3 | `0xf9 0x1c` | 3705 | Cell 3.705V |

Notes

* "No battery present" should be indicated as just the battery index with no cell voltages, e.g. `0x00 0x01 0x00` for battery 0 not present.
* No total voltage is transmitted, the total voltage should be the sum of the cell voltages.
* Do not send trailing zeroes for cells which are not present (e.g. do not always send 8 cells if only 4 are detected).
* Implementations may choose to report total battery voltage using this compound sensor by reporting the total voltage in Cell Voltage 0. However, in that case they **must not** also publish cell voltages.
* Only one battery per report.

### Example: ESC Monitoring (eType 0x01)

**Purpose**: ESC telemetry items. Not present in CRSF

| Field | Type | Description |
|--|--|--|
| eType | `UINT` | `0x01` / `1` |
| Length | `UINT` | Total encoded length of all data in this message |
| ESC Index | `UINT` | 0-based index of ESC |
| RPM | `UINT` | Optional. RPM value in 10s of RPM |
| ESC Temperature | `UINT` | Optional. Temperature C +40 |
| Battery Voltage | `UINT` | Optional. Total battery voltage in centivolts (0.01V) |
| Current | `UINT` | Optional. Current in deciamps (0.1A) |
| Motor Temperature | `UINT` | Optional. Motor Temperature C +40 |
| ESC Status | `UINT` | Optional. Bits 0-3: Demag Metric. Bit 4: Desync Event. Bit 5: Stall Event |

Example Payload: `0x01 0x0a 0x02 0xf9 0x09 0x56 0xd7 0x12 0xc3 0x01 0x7b 0x11`

| Field | Bytes | Value | Description |
|--|--|--|--|
| eType | `0x01` | 1 | ESC compound sensor begin |
| Length | `0x0a` | 10 | 10 bytes follow |
| ESC Index | `0x02` | 2 | ESC 2 (third ESC) |
| RPM | `0xf9 0x09` | 1273 | 12730 RPM |
| ESC Temperature | `0x56` | 86 | 46C |
| Battery Voltage | `0xd7 0x12` | 2391 | 23.91V |
| Current | `0xc3 0x01` | 195 | 19.5A |
| Motor Temperature | `0x7b` | 123 | 83C |
| ESC Status | `UINT` | `0x11` | 17 | Demag Metric: 1. Desync Event: True. Stall Event: False |

Notes

* Temperature reported with a value of 0 means not available (not -40C).
* ESC Status bits map directly to DSHOT-EDT status bits.
* Fill only the optional fields to the last available value. i.e. do not fill the entire report with zeroes if the values are not available.
* Only one ESC per report.

### Example: BEC Monitoring (eType 0x02)

**Purpose**: BEC telemetry items. Not present in CRSF

| Field | Type | Description |
|--|--|--|
| eType | `UINT` | `0x02` / `2` |
| Length | `UINT` | Total encoded length of all data in this message |
| BEC Index | `UINT` | 0-based index of BEC |
| BEC Current Out | `UINT` | Optional. Output current (0.1A) |
| BEC Voltage In | `UINT` | Optional. Input Voltage (0.01V) |
| BEC Voltage Out | `UINT` | Optional. Output Voltage (0.01V) |
| BEC Temperature | `UINT` | Optional. BEC Temperature C +40 |

Example Payload: `0x02 0x07 0x01 0x17 0x84 0x09 0x82 0x05 0x4e`

| Field | Bytes | Value | Description |
|--|--|--|--|
| eType | `0x02` | 2 | BEC compound sensor begin |
| Length | `0x07` | 7 | 7 bytes follow |
| BEC Index | `0x01` | BEC 1 (second BEC) |
| BEC Current Out | `0x17` | 23 | 2.3A |
| BEC Voltage In | `0x84 0x09` | 1156 | 11.56V |
| BEC Voltage Out | `0x82 0x05` | 6407 | 6.42V |
| BEC Temperature | `0x4e` | 78 | 38C |

Notes

* Temperature reported with a value of 0 means not available (not -40C).
* Fill only the optional fields to the last available value. i.e. do not fill the entire report with zeroes the the values are not available.
* Only one BEC per report

### Example: Model Name (eType 0x03)

**Purpose**: The control link receiver (ExpressLRS / Crossfire) or Flight Controller may use this field to identify itself to the handset on connection. This can be used by the handset to verify the correct model is selected, or auto-select another model in response to the connection. Not present in CRSF

| Field | Type | Description |
|--|--|--|
| eType | `UINT` | `0x03` / `3` | 
| Length | `UINT` | Total encoded length of all data in this message |
| Model Name | `STRING` | User-entered model name |

Example Payload: `0x03 0x0c 0x0b 0x47 0x65 0x6e 0x74 0x6c 0x65 0x20 0x4c 0x61 0x64 0x79`

| Field | Bytes | Value | Description |
|--|--|--|--|
| eType | `0x03` | 3 | Model Name compound sensor begin |
| Length | `0x0c` | 12 | 12 bytes follow |
| Model Name | `0x0b 0x47 0x65 0x6e 0x74 0x6c 0x65 0x20 0x4c 0x61 0x64 0x79` | Gentle Lady | 11 chars

Note

* Maxium encoded Model Name size 20

### Example: Battery (eType 0x08)

**Purpose**: Comprehensive battery overview including voltage, current, consumption, and remaining percentage. Present in CRSF, but with lower precision.

| Field | Type | Description |
|--|--|--|
| eType | `UINT` | `0x08` / `8`
| Length | `UINT` | Total encoded length of all data in this message |
| Battery Index | `UINT` | 0-based battery index |
| Total Voltage | `UINT` | Total battery voltage in centivolts (0.01V) |
| Total Current | `UINT` | Current in centiamps (0.01A) |
| mAh Usage | `UINT` | mAh consumed this session |
| Remaining % | `UINT` | Estimated battery percentage remaining |

Example payload: `0x08 0x08 0x00 0xd9 0x13 0xeb 0x0b 0x97 0x11 0x14`

| Field | Bytes | Value | Description |
|--|--|--|--|
| eType | `0x08` | 8 | Battery compound sensor begin |
| Length | `0x08` | 8 | 8 bytes follow |
| Battery Index | `0x00` | 0 | First battery |
| Total Voltage | `0xd9 0x13` | 2521 | 25.21V |
| Total Current | `0xeb 0x0b` | 1899 | 18.99A |
| Used capacity | `0x97 0x11` | 2199 | 2199mAh used  |
| Remaining % | `0x14` | 20 | 20% remaining |

Notes

* One battery per report

### Example: Baro Alt (eType 0x09)

**Purpose**: Strictly to demonstrate `INT` data type and method for evaluating proper type for sensor items. Present in CRSF.

| Field | Type | Description |
|--|--|--|
| eType | `UINT` | `0x09` / `9` |
| Length | `UINT` | Total encoded length of all data in this message |
| Altitude | `UINT` | Altitude in decimeters (0.1m) +1000m (10000) |
| VSpd | `INT` | Optional. Vertical speed in centimeters/s (0.01m/s) |

Example payload: `0x09 0x04 0xf4 0x4e 0xac 0x02`

| Field | Bytes | Value | Description |
|--|--|--|--|
| eType | `0x09` | 9 | BaroAlt compound sensor begin |
| Length | `0x04` | 4 | 4 bytes follow |
| Altitude | `0xf4 0x4e` | 10100 | Altitude +10.0m |
| VSpd | `0xac 0x02` | 300 | VSpd +1.5 m/s |

Notes

* DESIGN: Altitude would likely be better expressed as `INT` type. Largest UINT+1000m that can be expressed in 2 bytes is around -1000m to 638.3m. Largest INT(no offset) that can be expressed in 2 bytes is -819.1m to 819.1m. No positive altitude can be expressed in 1 byte with UINT+1000m, INT=6.3m so size at 1 byte is not worth considering. 3 bytes UINT -1000m/208715.1m, INT -104857.5/104857.5m.

## Reseved eTypes

This specification reserves eType IDs 128-383 exclusively for custom user compound sensors. These IDs are guaranteed to never be used by crsf-wg.

## CRSF-E Versioning

Software implementations that utilize the CRSF-E frame type may specify which revision of the the spec they require as a cross-software way to indicate what sensors are supported. The proper formatting for this version number is `CRSF-Exxx` where `xxx` is the version number with no leading zeroes and no whitespace. For example, a flight controller might indicate it requires "version 7 or higher" to support the sensors it publishes-- this is denoted as `CRSF-E7 Required`. A handset which displays sensor data might indicate it supports up to version 21 and below denoted as `CRSF-E21 Support`.