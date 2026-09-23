# CRSF-MSP-to-AdHoc — CRSF and MSP firmware headers → AdHoc protocol description

> One of the [**converters to AdHoc protocol**](https://github.com/AdHoc-Protocol#converters-to-adhoc-protocol).
> Take a protocol you already have, get an [AdHoc](https://github.com/AdHoc-Protocol/AdHoc-protocol) description,
> open it in the Observer. The result is a starting point you refine by hand, not a finished protocol.

Two RC-link protocols of the drone / FPV world, neither of which has a formal schema:

- **CRSF** (TBS Crossfire), the serial protocol between a handset, its transmitter module, the receiver and the
  flight controller; ExpressLRS speaks it end to end.
- **MSP** (MultiWii Serial Protocol), the request/reply protocol between a configurator, OSD or VTX and a
  Betaflight / iNav flight controller.

The machine-readable part of both lives in the C headers of the reference firmware, so the converter is a small
C-header reader plus hand-maintained tables for the parts that exist only in prose (crsf-wg wiki) or in C code
(`msp.c`). The project is self-contained: it carries its own copy of the AdHoc emitter helpers
(`src/org/unirail/adhoc/`) and its own `validate.sh`.
## Before and after

`samples/crsf/crsf_protocol.h` is 553 lines of packed C structs; here is a 22-line window of it
([source](samples/crsf/crsf_protocol.h)) beside what the converter makes of it ([result](AdHoc/CRSF.cs)).
The battery frame is the interesting one: four C bit fields, whose widths are real claims about the values, so
they survive as `[MinMax]` ranges that AdHoc bit-packs itself.

```c
//CRSF_FRAMETYPE_BATTERY_SENSOR
typedef struct crsf_sensor_battery_s
{
    unsigned voltage : 16;  // mv * 100 BigEndian
    unsigned current : 16;  // ma * 100
    unsigned capacity : 24; // mah
    unsigned remaining : 8; // %
} PACKED crsf_sensor_battery_t;

// CRSF_FRAMETYPE_BARO_ALTITUDE
typedef struct crsf_sensor_baro_vario_s
{
    uint16_t altitude; // Altitude in decimeters + 10000dm, or Altitude in meters if high bit is set, BigEndian
    int16_t verticalspd;  // Vertical speed in cm/s, BigEndian
} PACKED crsf_sensor_baro_vario_t;

// CRSF_FRAMETYPE_AIRSPEED
typedef struct crsf_sensor_airspeed_s
{
    uint16_t speed;             // Airspeed in 0.1 * km/h (hectometers/h)
} PACKED crsf_sensor_airspeed_t;
// ... 23 more payload structs, the frame-type and address enums, and the framing #defines
```

```csharp
class BATTERY_SENSOR {
    // CRSF frame type byte - the source protocol's identity, not this pack's AdHoc id.
    public const int frame_type = 0x8;
    /**
    mv * 100 BigEndian
    */
    [Bits(16), MinMax(0, 65535)] ushort voltage;
    // ... current, same shape
    /**
    mah
    */
    [Bits(24), MinMax(0, 16777215)] uint capacity;
    [Bits(8), MinMax(0, 255)] byte remaining;
}

class BARO_ALTITUDE {
    public const int frame_type = 0x9;
    // physics: dm + 10000 offset, so ground level sits at 10000 -> [A(10000)] would pay, but the high bit switches the scale to metres; settle that first
    ushort altitude;
    // physics: vertical speed in cm/s, centred on zero, typically well under 1 000 -> consider [X(3_000)]
    short verticalspd;
}
```

The frame type left the Dashboard and became a `const`: a pack id is AdHoc's own matter and the agent assigns it.
The `// physics:` comments are the converter handing the reader a decision it cannot take itself — see
[Limitations](#limitations).


## Sources (all links verified)

| What | Link |
|:--|:--|
| CRSF specification wiki (frame table, per-frame pages, addresses, framing) | https://github.com/crsf-wg/crsf/wiki |
| CRSF wiki as a git repository (cloned into `samples/crsf.wiki`) | https://github.com/crsf-wg/crsf.wiki.git (clone-only: `git clone` works, a browser gets 404) |
| ExpressLRS reference header `crsf_protocol.h` (frame types, addresses, packed payload structs) | https://github.com/ExpressLRS/ExpressLRS/blob/master/src/include/crsf_protocol.h |
| ExpressLRS CRSF implementation folder | https://github.com/ExpressLRS/ExpressLRS/tree/master/src/lib/CrsfProtocol |
| Betaflight MSP v1 command ids `msp_protocol.h` | https://github.com/betaflight/betaflight/blob/master/src/main/msp/msp_protocol.h |
| Betaflight MSP v2 command ids | https://github.com/betaflight/betaflight/blob/master/src/main/msp/msp_protocol_v2_common.h and https://github.com/betaflight/betaflight/blob/master/src/main/msp/msp_protocol_v2_betaflight.h |
| Betaflight MSP handlers `msp.c` (payload layouts, reference only) | https://github.com/betaflight/betaflight/blob/master/src/main/msp/msp.c |
| MSP v2 framing | https://github.com/iNavFlight/inav/wiki/MSP-V2 |
| AdHoc protocol description format | https://github.com/AdHoc-Protocol |

## Layout

```
src/org/unirail/CHeaders2AdHoc.java   the converter: C-header reader + CRSF emitter + MSP emitter + hand tables
src/org/unirail/adhoc/                local copy of the AdHoc emitter helpers (AdHocWriter, Json)
fetch-samples.sh                      downloads the headers, msp.c and clones the crsf-wg wiki into samples/
build.sh                              javac + run over samples/ into AdHoc/
validate.sh                           AdHocAgent parse-only validation of AdHoc/*.cs
samples/crsf/crsf_protocol.h          ExpressLRS header
samples/crsf.wiki/                    crsf-wg wiki pages
samples/msp/                          Betaflight MSP headers and msp.c
AdHoc/CRSF.cs, AdHoc/MSP.cs           generated descriptors (+ *.branches.txt written by validate.sh)
```

## What the converter parses from the headers

| C construct | AdHoc |
|:--|:--|
| `#define NAME 123`, `0xC8`, `1 << n`, `bit(n)`, `"str"` | `public const long/string` in a constants container (`Framing`, `ChannelValue`, `MspTunnel` for CRSF; `Constants` for MSP); MSP command defines → `enum MSP_COMMAND` + one RPC each |
| `typedef enum [: uint8_t] { A = 0x02, B, … } name_e;` | `enum name` (suffix `_e` stripped); implicit values computed; a single-member enum becomes a constants container (AdHoc rejects 1-member enums) |
| anonymous `enum { … };` | constants merged into `Framing` |
| `typedef struct tag_s [: base_t] { … } PACKED name_t;` | `class name [: base]` (suffixes `_t`/`_s` stripped); the frame packs take the struct's fields |
| `uint8_t x;` … `int32_t`, `float`, `char` | `byte sbyte ushort short uint int ulong long float char` |
| `T x[N];` | `[D(N)] T[] x;` |
| `char s[N];` | `[D(+N)] string s;` |
| `T x[0];` (flexible array member) | `[D(255)] T[,,] x;` |
| `unsigned ch0 : 11;`, `int32_t rpm0 : 24;` | `[Bits(11), MinMax(0, 2047)] ushort ch0;`, `[Bits(24), MinMax(-8388608, 8388607)] int rpm0;` — narrowest AdHoc type for the width, range as a hard `MinMax` |
| `crsf_frame_type_e type;` (enum-typed field) | field of the AdHoc enum |
| `//` and `/* */` comments above / beside a declaration | doc comments |

Frame ↔ struct association is automatic when the header announces it (`// CRSF_FRAMETYPE_BATTERY_SENSOR` right
above the struct); the four structs the header does not annotate are bound by the `STRUCT_BY_FRAME` table.

## Hand-maintained tables (in `CHeaders2AdHoc.java`)

Every name in these tables is looked up in the parsed headers; a missing symbol aborts the conversion with a message
naming the row, so an upstream rename cannot silently produce stale output.

**CRSF** — `STRUCT_BY_FRAME` (frame → header struct: RC_CHANNELS_PACKED, LINK_STATISTICS, DEVICE_INFO, HANDSET),
`FRAME_PREFIX_FIELDS` / `FRAME_EXTRA_FIELDS` (DEVICE_INFO display name, ExpressLRS optional `armStatus` byte), and
`WIKI_PAYLOADS` for the 17 frames whose payload is described only by the wiki page of the same name (DEVICE_PING,
HEARTBEAT, PARAMETER_READ/WRITE/SETTINGS_ENTRY, COMMAND, ELRS_STATUS, MSP_REQ/RESP/WRITE, SUBSET_RC_CHANNELS_PACKED,
LINK_RX_ID, LINK_TX_ID, DISPLAYPORT_CMD, KISS_REQ/RESP, ARDUPILOT_RESP). Frames with neither struct nor table would
be emitted with a raw `byte[,,] payload` (none today).

**MSP** — `MSP_PAYLOADS`, each row naming the `msp.c` function the layout was transcribed from:

| Command | Side | Read from |
|:--|:--|:--|
| MSP_API_VERSION, MSP_FC_VARIANT, MSP_FC_VERSION, MSP_BOARD_INFO, MSP_ANALOG, MSP_BATTERY_STATE | reply | `mspCommonProcessOutCommand` |
| MSP_STATUS, MSP_RAW_IMU, MSP_ATTITUDE, MSP_ALTITUDE, MSP_RC, MSP_RAW_GPS, MSP_COMP_GPS, MSP_MOTOR, MSP_RC_TUNING, MSP_PID | reply | `mspProcessOutCommand` |
| MSP_SET_RAW_RC, MSP_SET_MOTOR, MSP_SET_PID | request | `mspProcessInCommand` |

`PidGains` (P/I/D triple, `NESTED` table) is the shared sub-pack of MSP_PID / MSP_SET_PID. All other commands get an
empty request pack and a raw `[D(65_535)] byte[,,] payload` reply (or request, for `in message` commands) with a
doc line saying the layout is not modelled.

## Generated descriptors

**`AdHoc/CRSF.cs`** (`namespace org.crsf`, project `CRSF`)
- Dashboard: 36 packs, none carrying an id — a pack id is AdHoc's internal matter and the agent assigns it. The
  CRSF frame type stays where it belongs: in `enum crsf_frame_type`, and as `public const int frame_type` inside
  each frame pack, so a migration can be audited against the source.
- Constants containers `Framing`, `ChannelValue`, `MspTunnel`; enums `crsf_frame_type` (header entries + the
  CRSFv3 frames documented only in the wiki), `crsf_addr`, `crsf_value_type`, `crsf_subcommand`; the one-member
  `crsf_command` is a constants container.
- Hosts `Handset` ↔ `FlightController`, connection `CrsfLink` with three non-transitional states: `Telemetry`
  (`____________r`, 13 sensor frames), `Control` (`l____________`, the two channel frames), `Extended`
  (`_____lr_____`, heartbeat, link statistics and all extended-header frames). Direction comes from the wiki table's
  Telemetry column and the `crsf_sensor_*` struct prefix.
- Custom attributes `[Bits(n)]` (C bit-field width) and `[ExtendedHeader]` (frame types ≥ 0x28);
  `_DefaultMaxLengthOf` raising every collection cap to 65 535, because parameter values and tunnelled MSP frames
  are chunked across frames and reassembled well past AdHoc's 255-item default.

**`AdHoc/MSP.cs`** (`namespace org.msp`, project `MSP`)
- `enum MSP_COMMAND` with all 191 v1 + v2 ids; `Constants` (API version, identifier lengths, capability bits,
  MSP2_GET_TEXT variable ids); `Framing` (v1/v2 preamble, direction chars, payload limits); `_DefaultMaxLengthOf`
  raising every collection cap to 65 535, because an MSP v2 payload reaches 65 535 bytes and AdHoc's 255-item
  default would silently truncate it.
- One `MSP_XXX_Request` / `MSP_XXX_Reply` pair per command, both carrying `public const int msp_id`; no pack is
  pinned in the Dashboard — the MSP command id is the source protocol's identity, not an AdHoc pack id, and lives
  in `enum MSP_COMMAND` and in that `const`.
- Hosts `Configurator` ↔ `FlightController`, connection `MspLink` with one RPC per command:
  `(L____________, MSP_XXX_Reply) MSP_XXX(MSP_XXX_Request req);` — the agent synthesises 191 two-state actors.

## Build, run, validate

```bash
./fetch-samples.sh      # headers + msp.c + wiki clone → samples/
./build.sh              # javac → out/, then: java -cp out org.unirail.CHeaders2AdHoc samples AdHoc
./validate.sh AdHoc     # AdHocAgent ADHOC_PARSE_ONLY=1 over AdHoc/*.cs; writes AdHoc/*.branches.txt
```

CLI: `java -cp out org.unirail.CHeaders2AdHoc <samples folder> [output folder]`; output defaults to `<cwd>/AdHoc`.
Java 17+.

## Validation result (samples fetched 2026-09-22, Betaflight master, ExpressLRS master)

```
CRSF                             OK
MSP                              OK
```

No errors or warnings from the agent. `AdHoc/CRSF.branches.txt` shows the three states with 13 / 2 / 17 packs and
the packs of each; `AdHoc/MSP.branches.txt` shows 191 Call→Return actors. All pack ids are agent-assigned; the
source numbers are checked in the `frame_type` / `msp_id` constants instead.

## Limitations

- CRSF multi-byte fields are big-endian on the wire and several use non-byte-aligned bit packing; AdHoc has its own
  little-endian, bit-packed encoding, so the descriptors model the *data*, not the CRSF byte layout. `[Bits(n)]`
  keeps the original width as metadata.
- Optional trailing fields (BARO_ALTITUDE's vertical speed, RC_CHANNELS_PACKED's arm byte) are modelled as
  nullable / plain fields, not as "present when the frame is longer".
- `PARAMETER_SETTINGS_ENTRY` / `PARAMETER_WRITE` values are type-dependent byte sequences; they stay raw bytes.
- MSP: only the 18 commands listed above have typed payloads; everything else is raw bytes. `MSP_STATUS_EX` shares
  most of `MSP_STATUS`'s layout but is not typed. MSP is only Betaflight's dialect; iNav-specific ids are absent.
- The header reader is line-oriented: it handles the constructs in these headers (single-line fields, `typedef enum`
  with or without `{` on the same line, `PACKED` structs, C++ base struct) and nothing more elaborate.
- A section comment placed directly above the first `#define` of a group (e.g. "Multiwii original MSP commands
  (101-139)") is attached to that define's doc.
- **No varint attribute is emitted, but the question is never dropped.** A C header declares a *width*, not where
  the values sit, and a width is not a distribution — how CRSF or MSP arranges its own bytes decides nothing here,
  because AdHoc lays out its own frame. So `[A]` / `[V]` / `[X]` would be a guess, and on a uniformly distributed
  field a varint makes the wire *bigger*. What the sources do state — in units (`cdegC`, `cm/s`, `mAh`,
  radians × 10000), in field names and in comments — is carried to the field as a `// physics:` note naming the
  candidate and the reason, so the decision is taken where it belongs, by someone with real traffic in hand.
  47 fields carry one: attitude, vario and IMU axes are centred on zero (`[X]`); consumption counters, distances
  and error counters climb from zero (`[A]`); channel values, motor outputs, headings and percentages are hard
  ranges that bit-pack tighter than any varint (`[MinMax]`); and scaled coordinates, identity words and bitmasks
  are named as varint *losses* so nobody tries. The arithmetic is stated once per file: a varint wins while the
  typical distance from its base stays under about two million, breaks even to 268 435 455, and always loses
  beyond that.
- Bit-field widths, which *are* a claim about a range, stay as hard `[MinMax]` ranges, which AdHoc bit-packs.
- No pack id is pinned: source numbers live in the protocol's own enum and in a `const` inside each pack, and the
  agent assigns pack ids freely.
- Agent quirks respected by the emitter: enums carry no base type (or `: long` when a value needs it) and constants
  are `long` / `string` / `char` only, because `short` / `sbyte` enum bases and constants crash AdHocAgent; each
  custom attribute appears at most once per field; no pack, enum or container is named like an `org.unirail.Meta`
  member (`Binary`, `Map`, `Set`, `Duration`, `File`, `Stream`, `Host`, `Actor`).
