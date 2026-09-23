package org.unirail;

import org.unirail.adhoc.AdHocWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.unirail.adhoc.AdHocWriter.I1;
import static org.unirail.adhoc.AdHocWriter.I2;
import static org.unirail.adhoc.AdHocWriter.I3;
import static org.unirail.adhoc.AdHocWriter.I4;
import static org.unirail.adhoc.AdHocWriter.doc;
import static org.unirail.adhoc.AdHocWriter.ident;
import static org.unirail.adhoc.AdHocWriter.str;

/**
 * CRSF + MSP → AdHoc protocol description converter.
 *
 * <p>Neither RC-link protocol has a formal schema; the machine-readable part lives in the C headers of the
 * reference firmware, so this tool is a small C-header reader:
 * <ul>
 *   <li>{@code #define NAME value} lines → constants / MSP command ids;</li>
 *   <li>{@code typedef enum { … }} blocks → enums;</li>
 *   <li>{@code typedef struct … PACKED name_t;} blocks (fields, arrays, bit-fields, C++ base struct) → packs.</li>
 * </ul>
 * What the headers do not carry - CRSF frames documented only in the crsf-wg wiki, and MSP payload layouts that
 * exist only as {@code sbufWrite…} sequences in {@code msp.c} - is kept in hand-maintained tables at the bottom of
 * this file, each row naming the upstream page / function it was transcribed from. Every hand-written name is
 * checked against the parsed headers, so a renamed or removed upstream symbol fails the conversion loudly.
 *
 * <p>Usage: {@code java -cp out org.unirail.CHeaders2AdHoc <samples folder> [output folder]} where the samples
 * folder holds {@code crsf/crsf_protocol.h}, {@code crsf.wiki/Packet-Types.md} (optional) and
 * {@code msp/msp_protocol*.h}. Output: {@code CRSF.cs} and {@code MSP.cs}.
 */
public class CHeaders2AdHoc {

	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			System.out.println("Usage: java -cp out org.unirail.CHeaders2AdHoc <samples folder> [output folder]");
			System.out.println("       samples/crsf/crsf_protocol.h, samples/crsf.wiki/Packet-Types.md, samples/msp/msp_protocol*.h");
			return;
		}
		Path samples = Paths.get(args[0]);
		Path out = 1 < args.length ? Paths.get(args[1]) : Paths.get(System.getProperty("user.dir"), "AdHoc");
		Files.createDirectories(out);
		int failed = 0;
		try {
			Path f = out.resolve("CRSF.cs");
			Files.write(f, new Crsf(samples).emit().getBytes(StandardCharsets.UTF_8));
			System.out.println("CRSF -> " + f);
		} catch (Exception e) {
			failed++;
			System.err.println("FAILED CRSF: " + e);
			e.printStackTrace();
		}
		try {
			Path f = out.resolve("MSP.cs");
			Files.write(f, new Msp(samples).emit().getBytes(StandardCharsets.UTF_8));
			System.out.println("MSP  -> " + f);
			checkPhysicsUsed();   // both files are emitted: every PHYSICS row must have matched a real field
		} catch (Exception e) {
			failed++;
			System.err.println("FAILED MSP: " + e);
			e.printStackTrace();
		}
		if (0 < failed) System.exit(2);
	}

	// ═══════════════════════════════════════════ C header model ═══════════════════════════════════════════

	static final class Define {
		String name, doc = "", raw = "";
		Long value;      // numeric value when the macro is an integer literal, `1 << n`, `bit(n)` or `(uint32_t)1 << n`
		String string;   // string literal macros
	}

	static final class Entry {
		String name, literal, doc = "";
		long value;
	}

	static final class CEnum {
		String name, doc = "";        // name is null for an anonymous `enum { … };`
		final List<Entry> entries = new ArrayList<>();
	}

	static final class CField {
		String ctype, name, doc = "";
		int array = -1;               // -1 scalar, 0 flexible array member `x[0]`, N constant array
		int bits;                     // bit-field width, 0 when not a bit-field
	}

	static final class CStruct {
		String name, tag, base, doc = "", precedingComment = "";
		final List<CField> fields = new ArrayList<>();
	}

	/** Line-oriented reader for the subset of C the reference headers use. */
	static final class CHeader {
		final List<Define> defines = new ArrayList<>();
		final List<CEnum> enums = new ArrayList<>();
		final List<CStruct> structs = new ArrayList<>();

		static final Pattern DEFINE = Pattern.compile("^#define\\s+([A-Za-z_]\\w*)(\\([^)]*\\))?\\s*(.*)$");
		static final Pattern ENUM_START = Pattern.compile("^(?:typedef\\s+)?enum\\b(?:\\s*:\\s*\\w+)?\\s*(\\{)?\\s*$");
		static final Pattern ENUM_ENTRY = Pattern.compile("^([A-Za-z_]\\w*)\\s*(?:=\\s*([^,/]+?))?\\s*,?\\s*(//.*)?$");
		static final Pattern BLOCK_END = Pattern.compile("^\\}\\s*(?:PACKED\\s+)?([A-Za-z_]\\w*)?\\s*;");
		static final Pattern STRUCT_START = Pattern.compile("^typedef\\s+struct\\s*([A-Za-z_]\\w*)?\\s*(?::\\s*([A-Za-z_]\\w*))?\\s*(\\{)?\\s*$");
		static final Pattern FIELD = Pattern.compile("^([A-Za-z_]\\w*)\\s+([A-Za-z_]\\w*)\\s*(?:\\[(\\d+)\\])?\\s*(?::\\s*(\\d+))?\\s*;\\s*(//.*)?$");
		static final Pattern SHIFT = Pattern.compile("^\\(?\\s*(?:\\(\\s*u?int\\d+_t\\s*\\)\\s*)?1\\s*<<\\s*(\\d+)\\s*\\)?$");
		static final Pattern BIT = Pattern.compile("^bit\\((\\d+)\\)$");

		static CHeader parse(Path file) throws IOException {
			CHeader h = new CHeader();
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			StringBuilder pending = new StringBuilder();   // standalone comment lines waiting for a declaration
			boolean inBlockComment = false;
			CEnum en = null;
			CStruct st = null;
			boolean awaitingBrace = false;

			for (String raw : lines) {
				String line = raw.trim();

				// ── comments ──
				if (inBlockComment) {
					int end = line.indexOf("*/");
					if (end < 0) { addComment(pending, line); continue; }
					addComment(pending, line.substring(0, end));
					inBlockComment = false;
					line = line.substring(end + 2).trim();
					if (line.isEmpty()) continue;
				}
				if (line.startsWith("/*")) {
					int end = line.indexOf("*/", 2);
					if (end < 0) { addComment(pending, line.substring(2)); inBlockComment = true; continue; }
					addComment(pending, line.substring(2, end));
					line = line.substring(end + 2).trim();
					if (line.isEmpty()) continue;
				}
				if (line.startsWith("//")) { addComment(pending, line.substring(2)); continue; }
				if (line.isEmpty()) continue;

				// ── inside an enum ──
				if (en != null) {
					if (awaitingBrace) { if (line.startsWith("{")) { awaitingBrace = false; line = line.substring(1).trim(); if (line.isEmpty()) continue; } }
					Matcher m = BLOCK_END.matcher(line);
					if (m.find()) {
						en.name = m.group(1);
						h.enums.add(en);
						en = null;
						pending.setLength(0);
						continue;
					}
					m = ENUM_ENTRY.matcher(line);
					if (m.matches()) {
						Entry e = new Entry();
						e.name = m.group(1);
						e.literal = m.group(2) == null ? null : m.group(2).trim();
						e.value = e.literal == null ? (en.entries.isEmpty() ? 0 : en.entries.get(en.entries.size() - 1).value + 1) : Long.decode(e.literal);
						e.doc = take(pending) + comment(m.group(3));
						en.entries.add(e);
					}
					continue;
				}

				// ── inside a struct ──
				if (st != null) {
					if (awaitingBrace) { if (line.startsWith("{")) { awaitingBrace = false; line = line.substring(1).trim(); if (line.isEmpty()) continue; } }
					Matcher m = BLOCK_END.matcher(line);
					if (m.find()) {
						st.name = m.group(1) != null ? m.group(1) : st.tag;
						h.structs.add(st);
						st = null;
						pending.setLength(0);
						continue;
					}
					m = FIELD.matcher(line);
					if (m.matches()) {
						CField f = new CField();
						f.ctype = m.group(1);
						f.name = m.group(2);
						if (m.group(3) != null) f.array = Integer.parseInt(m.group(3));
						if (m.group(4) != null) f.bits = Integer.parseInt(m.group(4));
						f.doc = take(pending) + comment(m.group(5));
						st.fields.add(f);
					}
					continue;
				}

				// ── top level ──
				Matcher m = DEFINE.matcher(line);
				if (m.matches()) {
					if (m.group(2) != null) { pending.setLength(0); continue; } // function-like macro
					Define d = new Define();
					d.name = m.group(1);
					String v = m.group(3);
					int c = v.indexOf("//");
					if (0 <= c) { d.doc = comment(v.substring(c)); v = v.substring(0, c); }
					v = v.trim();
					if (v.endsWith(";")) v = v.substring(0, v.length() - 1).trim();
					d.raw = v;
					d.doc = (take(pending) + d.doc).trim();
					if (v.startsWith("\"") && v.endsWith("\"") && 1 < v.length()) d.string = v.substring(1, v.length() - 1);
					else {
						try { d.value = Long.decode(v); } catch (NumberFormatException ignored) {
							Matcher s = SHIFT.matcher(v);
							Matcher b = BIT.matcher(v);
							if (s.matches()) d.value = 1L << Integer.parseInt(s.group(1));
							else if (b.matches()) d.value = 1L << Integer.parseInt(b.group(1));
						}
					}
					h.defines.add(d);
					continue;
				}
				m = ENUM_START.matcher(line);
				if (m.matches()) {
					en = new CEnum();
					en.doc = take(pending);
					awaitingBrace = m.group(1) == null;
					continue;
				}
				m = STRUCT_START.matcher(line);
				if (m.matches()) {
					st = new CStruct();
					st.tag = m.group(1);
					st.base = m.group(2);
					st.precedingComment = pending.toString();
					st.doc = take(pending);
					awaitingBrace = m.group(3) == null;
					continue;
				}
				// anything else (functions, pragmas, includes) breaks the comment association
				pending.setLength(0);
			}
			return h;
		}

		static void addComment(StringBuilder pending, String text) {
			String t = text.trim();
			while (t.startsWith("*")) t = t.substring(1).trim();
			if (t.isEmpty() || t.chars().allMatch(ch -> ch == '/' || ch == '*' || ch == '-' || ch == '=' || ch == '#')) return;
			if (0 < pending.length()) pending.append('\n');
			pending.append(t);
		}

		static String comment(String c) {
			if (c == null) return "";
			String t = c.trim();
			while (t.startsWith("/")) t = t.substring(1);
			return t.trim();
		}

		static String take(StringBuilder pending) {
			String s = pending.toString().trim();
			pending.setLength(0);
			return s.isEmpty() ? "" : s + "\n";
		}

		Define define(String name) {
			for (Define d : defines) if (d.name.equals(name)) return d;
			return null;
		}

		CEnum enumNamed(String name) {
			for (CEnum e : enums) if (name.equals(e.name)) return e;
			return null;
		}

		CStruct struct(String name) {
			for (CStruct s : structs) if (name.equals(s.name)) return s;
			return null;
		}
	}

	// ═══════════════════════════════════════════ shared emission helpers ═══════════════════════════════════════════

	/**
	 * A pack id is AdHoc's own internal matter and the agent assigns it, so no source number is ever pinned into the
	 * Dashboard. The CRSF frame type and the MSP command id stay where they belong: in the protocol's own enum, and
	 * as a `const` inside each pack so a migration can be audited against the source.
	 */
	static void sourceId(StringBuilder sb, String indent, String name, String literal, String doc) {
		sb.append(indent).append("// ").append(doc).append('\n');
		sb.append(indent).append("public const int ").append(name).append(" = ").append(literal).append(";\n");
	}

	/**
	 * The `_DefaultMaxLengthOf` caps. AdHoc defaults every collection and string to 255 items, but an MSP v2 payload
	 * reaches 65535 bytes and a CRSF parameter chunk is bounded only by the frame, so 255 would silently truncate.
	 */
	static void defaultMaxLength(StringBuilder sb, String indent, String why) {
		doc(sb, indent, why);
		sb.append(indent).append("enum _DefaultMaxLengthOf {\n");
		sb.append(indent).append(I1).append("Arrays  = 65_535,\n");
		sb.append(indent).append(I1).append("Maps    = 65_535,\n");
		sb.append(indent).append(I1).append("Sets    = 65_535,\n");
		sb.append(indent).append(I1).append("Strings = 65_535,\n");
		sb.append(indent).append("}\n\n");
	}

	static String csType(String ctype) {
		switch (ctype) {
			case "uint8_t": return "byte";
			case "int8_t": return "sbyte";
			case "uint16_t": return "ushort";
			case "int16_t": return "short";
			case "uint32_t": case "unsigned": return "uint";
			case "int32_t": case "int": return "int";
			case "uint64_t": return "ulong";
			case "int64_t": return "long";
			case "float": return "float";
			case "double": return "double";
			case "bool": return "bool";
			case "char": return "char";
			default: return null;
		}
	}

	/** Strips the C naming suffixes `_t` / `_s` / `_e` so `crsf_sensor_gps_t` becomes `crsf_sensor_gps`. */
	static String plain(String cname) {
		String n = cname;
		if (n.endsWith("_t") || n.endsWith("_s") || n.endsWith("_e")) n = n.substring(0, n.length() - 2);
		return ident(n);
	}

	/** Emits one C struct field as an AdHoc field line (attributes, type, name). */
	static String field(CField f, Map<String, String> enumTypes) {
		List<String> attrs = new ArrayList<>();
		String type;
		boolean signed = f.ctype.startsWith("int");
		if (0 < f.bits) {
			attrs.add("Bits(" + f.bits + ")");
			long max = signed ? (1L << (f.bits - 1)) - 1 : (1L << f.bits) - 1;
			long min = signed ? -(1L << (f.bits - 1)) : 0;
			attrs.add("MinMax(" + min + ", " + max + ")");
			int w = f.bits <= 8 ? 8 : f.bits <= 16 ? 16 : 32;
			type = signed ? (w == 8 ? "sbyte" : w == 16 ? "short" : "int") : (w == 8 ? "byte" : w == 16 ? "ushort" : "uint");
		} else if (f.ctype.equals("char") && 0 < f.array) {
			attrs.add("D(+" + f.array + ")");
			type = "string";
		} else {
			type = enumTypes.get(f.ctype);
			if (type == null) type = csType(f.ctype);
			if (type == null) throw new IllegalStateException("Unknown C type `" + f.ctype + "` of field " + f.name);
			if (f.array == 0) { attrs.add("D(255)"); type += "[,,]"; }        // flexible array member: a list
			else if (0 < f.array) { attrs.add("D(" + f.array + ")"); type += "[]"; }
		}
		return (attrs.isEmpty() ? "" : "[" + String.join(", ", attrs) + "] ") + type + " " + ident(f.name) + ";";
	}

	/** Parses a hand-table field spec `type name // doc` (types: byte sbyte ushort short uint int, T[N], T[,,N], string(N), bool, Pack). */
	static final Pattern SPEC = Pattern.compile("^([A-Za-z_]\\w*)(\\?)?(?:\\[(\\d+)\\]|\\[,,(\\d+)\\]|\\((\\d+)\\))?\\s+([A-Za-z_]\\w*)\\s*(?://\\s*(.*))?$");

	static void specField(StringBuilder sb, String indent, String spec) { specField(sb, indent, spec, null); }

	static void specField(StringBuilder sb, String indent, String spec, String owner) {
		Matcher m = SPEC.matcher(spec.trim());
		if (!m.matches()) throw new IllegalArgumentException("Bad field spec: " + spec);
		String type = m.group(1), name = m.group(6), d = m.group(7);
		String attr = "";
		if (m.group(3) != null) { attr = "[D(" + m.group(3) + ")] "; type += "[]"; }
		else if (m.group(4) != null) { attr = "[D(" + m.group(4) + ")] "; type += "[,,]"; }
		else if (m.group(5) != null) { attr = "[D(+" + m.group(5) + ")] "; }
		if (m.group(2) != null) type += "?";
		doc(sb, indent, d);
		physics(sb, indent, owner, name);
		sb.append(indent).append(attr).append(type).append(' ').append(ident(name)).append(";\n");
	}

	// ── physics of a number ──

	/**
	 * Candidate varint attributes, one row per field the source tells us something about: {pack, field, note}.
	 * Neither CRSF nor MSP declares a distribution, but units, field names and comments imply one, and that is
	 * a statement about the data rather than about the source's wire format - AdHoc lays out its own frame, so
	 * "the C header stores this fixed-width" decides nothing.
	 *
	 * <p>These are emitted as comments, never as attributes: choosing `[A]`/`[V]`/`[X]` is a decision taken with
	 * real traffic in hand, and a converter cannot take it. What it must not do is drop the question silently.
	 * Every row is checked against the emitted model, so a renamed field fails the conversion.
	 */
	static final String[][] PHYSICS = {
			// CRSF — telemetry
			{"ATTITUDE", "pitch", "attitude in rad*10000, centred on zero, |v| <= 31416 -> consider [X(31416)]"},
			{"ATTITUDE", "roll", "attitude in rad*10000, centred on zero, |v| <= 31416 -> consider [X(31416)]"},
			{"ATTITUDE", "yaw", "attitude in rad*10000, centred on zero, |v| <= 31416 -> consider [X(31416)]"},
			{"VARIO", "verticalspd", "vertical speed in cm/s, centred on zero, typically well under 1 000 -> consider [X(3_000)]"},
			{"BARO_ALTITUDE", "verticalspd", "vertical speed in cm/s, centred on zero, typically well under 1 000 -> consider [X(3_000)]"},
			{"BARO_ALTITUDE", "altitude", "dm + 10000 offset, so ground level sits at 10000 -> [A(10000)] would pay, but the high bit switches the scale to metres; settle that first"},
			{"GPS", "latitude", "degrees * 1e7, systematically up to 1.8e9: varint always loses past 268 435 455 - leave fixed"},
			{"GPS", "longitude", "degrees * 1e7, systematically up to 1.8e9: varint always loses past 268 435 455 - leave fixed"},
			{"GPS", "altitude", "metres + 1000 offset, so ground level sits at 1000 and values climb from there -> consider [A(1000)]"},
			{"GPS", "gps_heading", "degrees * 100, a hard 0..36000 range, uniform within it -> consider [MinMax(0, 36_000)] (bit-packs to 16 bits)"},
			{"TEMP", "temperature", "deci-degrees Celsius, centred near ambient, |v| < 1 000 for anything survivable -> consider [X(1_000)] (1 byte per element instead of 2)"},
			{"CELLS", "cell", "cell voltage in mV, a hard 0..4500 range -> consider [MinMax(0, 4_500)]: 13 bits per cell across the array, where varint would be a wash"},
			// CRSF — timing and identity
			{"HANDSET", "rate", "packet interval in us*10 (30 030 at 333 Hz, 200 000 at 50 Hz): the distance from zero stays under two million, so varint pays -> consider [A]"},
			{"HANDSET", "offset", "phase-shift correction, a signed adjustment centred on zero -> consider [X]"},
			{"DEVICE_INFO", "serialNo", "an identity word, uniformly distributed across 32 bits: varint costs a fifth byte - leave fixed"},
			{"DEVICE_INFO", "hardwareVer", "a packed version word, uniformly distributed: varint costs a fifth byte - leave fixed"},
			{"DEVICE_INFO", "softwareVer", "a packed version word, uniformly distributed: varint costs a fifth byte - leave fixed"},
			// MSP
			{"MSP_ATTITUDE_Reply", "rollDecidegrees", "decidegrees, centred on zero, |v| <= 1800 -> consider [X(1_800)]"},
			{"MSP_ATTITUDE_Reply", "pitchDecidegrees", "decidegrees, centred on zero, |v| <= 1800 -> consider [X(1_800)]"},
			{"MSP_ATTITUDE_Reply", "yawDegrees", "a hard 0..359 range -> consider [MinMax(0, 359)] (9 bits)"},
			{"MSP_ALTITUDE_Reply", "altitudeCm", "estimated altitude in cm, centred on the launch point, |v| under a few hundred thousand -> consider [X(1_000_000)]"},
			{"MSP_ALTITUDE_Reply", "varioCmPerS", "vertical speed in cm/s, centred on zero -> consider [X(3_000)]"},
			{"MSP_RAW_IMU_Reply", "acc", "accelerometer counts, centred on zero -> consider [X]"},
			{"MSP_RAW_IMU_Reply", "gyro", "angular rate in deg/s, centred on zero and small in level flight -> consider [X(2_000)]"},
			{"MSP_RAW_IMU_Reply", "mag", "magnetometer counts, centred on zero -> consider [X]"},
			{"MSP_ANALOG_Reply", "mAhDrawn", "consumption from a full pack: starts at zero and climbs, never returns -> consider [A]"},
			{"MSP_ANALOG_Reply", "amperageCentiamps", "current in 0.01 A, a declared -320..320 A range centred on zero -> consider [X(32_000)]"},
			{"MSP_ANALOG_Reply", "rssi", "a hard 0..1023 range -> consider [MinMax(0, 1_023)] (10 bits)"},
			{"MSP_BATTERY_STATE_Reply", "mAhDrawn", "consumption from a full pack: starts at zero and climbs -> consider [A]"},
			{"MSP_BATTERY_STATE_Reply", "amperageCentiamps", "current in 0.01 A, a declared -320..320 A range centred on zero -> consider [X(32_000)]"},
			{"MSP_BATTERY_STATE_Reply", "voltageCentivolts", "pack voltage clusters tightly at the cell count times the nominal cell voltage; a hard [MinMax] fits it in bits, where a 16-bit varint would only break even"},
			{"MSP_STATUS_Reply", "i2cErrorCount", "an error counter that is zero on a healthy board -> consider [A]"},
			{"MSP_STATUS_Reply", "cpuLoadPercent", "a hard 0..100 range -> consider [MinMax(0, 100)] (7 bits)"},
			{"MSP_STATUS_Reply", "flightModeFlags", "a bitmask, every bit independent: varint has no leading zeroes to drop - leave fixed"},
			{"MSP_STATUS_Reply", "armingDisableFlags", "a bitmask, every bit independent: varint has no leading zeroes to drop - leave fixed"},
			{"MSP_RAW_GPS_Reply", "latitudeDegE7", "degrees * 1e7, systematically up to 1.8e9: varint always loses past 268 435 455 - leave fixed"},
			{"MSP_RAW_GPS_Reply", "longitudeDegE7", "degrees * 1e7, systematically up to 1.8e9: varint always loses past 268 435 455 - leave fixed"},
			{"MSP_RAW_GPS_Reply", "altitudeM", "altitude in metres above sea level, small and positive for almost every flight -> consider [A]"},
			{"MSP_RAW_GPS_Reply", "groundCourseDecidegrees", "a hard 0..3600 range -> consider [MinMax(0, 3_600)] (12 bits)"},
			{"MSP_COMP_GPS_Reply", "distanceToHomeM", "distance from the launch point: starts at zero and stays small -> consider [A]"},
			{"MSP_COMP_GPS_Reply", "directionToHomeDegrees", "a hard 0..359 range -> consider [MinMax(0, 359)] (9 bits)"},
			{"MSP_RC_Reply", "channels", "RC channel values, a hard 1000..2000 range -> consider [MinMax(1_000, 2_000)]: 10 bits per channel across the array"},
			{"MSP_SET_RAW_RC_Request", "channels", "RC channel values, a hard 1000..2000 range -> consider [MinMax(1_000, 2_000)]: 10 bits per channel across the array"},
			{"MSP_MOTOR_Reply", "motor", "motor outputs, a hard 0..2000 range (0 = disabled) -> consider [MinMax(0, 2_000)]: 11 bits per motor"},
			{"MSP_SET_MOTOR_Request", "motor", "motor outputs, a hard 0..2000 range -> consider [MinMax(0, 2_000)]: 11 bits per motor"},
	};

	static final Map<String, String> PHYSICS_BY_FIELD = new LinkedHashMap<>();
	static final Set<String> PHYSICS_SEEN = new HashSet<>();

	static {
		for (String[] row : PHYSICS)
			if (PHYSICS_BY_FIELD.put(row[0] + "." + row[1], row[2]) != null)
				throw new IllegalStateException("Duplicate PHYSICS row " + row[0] + "." + row[1]);
	}

	/** Emits the physics comment of `owner.field`, if there is one, and records that the row was used. */
	static void physics(StringBuilder sb, String indent, String owner, String fieldName) {
		if (owner == null) return;
		String note = PHYSICS_BY_FIELD.get(owner + "." + fieldName);
		if (note == null) return;
		PHYSICS_SEEN.add(owner + "." + fieldName);
		sb.append(indent).append("// physics: ").append(note).append('\n');
	}

	/** Fails loudly when a PHYSICS row names a pack or field that the emitted model no longer has. */
	static void checkPhysicsUsed() {
		List<String> unused = new ArrayList<>();
		for (String key : PHYSICS_BY_FIELD.keySet()) if (!PHYSICS_SEEN.contains(key)) unused.add(key);
		if (!unused.isEmpty())
			throw new IllegalStateException("PHYSICS rows that match no emitted field (upstream renamed or removed them): " + unused);
	}

	/** The varint arithmetic, stated once per file so the comments below it need not repeat it. */
	static void varintNote(StringBuilder sb, String indent) {
		doc(sb, indent, "This protocol declares widths, not distributions, so the converter emits no [A] / [V] / [X]: choosing one "
				+ "is a decision taken with real traffic in hand. Where a unit, a field name or a comment does imply where the values sit, "
				+ "the field carries a `// physics:` note naming the candidate.\n"
				+ "The arithmetic behind those notes: a varint costs one byte per 7 bits of distance from its base, so it wins while the "
				+ "typical distance stays under about two million, breaks even up to 268 435 455, and always loses beyond that. A span "
				+ "narrower than one byte is rejected outright and belongs in [MinMax], which bit-packs it.");
	}

	static void constants(StringBuilder sb, String indent, String name, String doc, Collection<Define> defs) {
		if (defs.isEmpty()) return;
		doc(sb, indent, doc);
		sb.append(indent).append("public struct ").append(name).append(" {\n");
		for (Define d : defs) {
			doc(sb, indent + I1, d.doc);
			if (d.string != null) sb.append(indent).append(I1).append("public const string ").append(ident(d.name)).append(" = ").append(str(d.string)).append(";\n");
			else sb.append(indent).append(I1).append("public const long ").append(ident(d.name)).append(" = ").append(d.raw.matches("-?\\d+|0[xX][0-9A-Fa-f]+") ? d.raw : Long.toString(d.value)).append(";\n");
		}
		sb.append(indent).append("}\n\n");
	}

	static void cEnum(StringBuilder sb, String indent, String name, CEnum e) {
		doc(sb, indent, e.doc);
		if (e.entries.size() < 2) {
			sb.append(indent).append("// C enum with a single member: AdHoc rejects such enums, kept as a constants container.\n");
			sb.append(indent).append("public struct ").append(name).append(" {\n");
			for (Entry x : e.entries) {
				doc(sb, indent + I1, x.doc);
				sb.append(indent).append(I1).append("public const long ").append(ident(x.name)).append(" = ").append(x.literal != null ? x.literal : Long.toString(x.value)).append(";\n");
			}
			sb.append(indent).append("}\n\n");
			return;
		}
		boolean wide = false;
		for (Entry x : e.entries) if (x.value < Integer.MIN_VALUE || Integer.MAX_VALUE < x.value) wide = true;
		sb.append(indent).append("enum ").append(name).append(wide ? " : long" : "").append(" {\n");
		for (Entry x : e.entries) {
			doc(sb, indent + I1, x.doc);
			sb.append(indent).append(I1).append(ident(x.name)).append(" = ").append(x.literal != null ? x.literal : Long.toString(x.value)).append(",\n");
		}
		sb.append(indent).append("}\n\n");
	}

	// ═══════════════════════════════════════════ CRSF ═══════════════════════════════════════════

	static final class WikiRow {
		int id;
		boolean extended, telemetry;
		String description = "";
	}

	static final class Frame {
		String enumName, packName, doc = "";
		long id;
		boolean telemetry, extended;
		CStruct struct;          // payload from the header
		String[] table;          // payload from the hand table (wiki)
		String[] extra;          // extra hand-table fields appended after the struct fields
		String[] prefix;         // hand-table fields prepended before the struct fields
	}

	static final class Crsf {
		final Path samples;
		final CHeader h;
		final Map<String, WikiRow> wiki = new LinkedHashMap<>();
		final LinkedHashMap<String, Frame> frames = new LinkedHashMap<>();
		final Map<String, String> enumTypes = new HashMap<>();     // C enum type → AdHoc enum name (only real enums)
		final Set<String> framedStructs = new HashSet<>();
		final Map<String, String> structPack = new HashMap<>();    // C struct name → AdHoc pack name

		static final Pattern WIKI_ROW = Pattern.compile("^\\|\\s*0x([0-9A-Fa-f]+)\\s*/\\s*\\d+\\s*\\|\\s*(Y?)\\s*\\|\\s*(Y?)\\s*\\|\\s*`?(?:\\[\\[)?(CRSF_FRAMETYPE_[A-Z0-9_]+)(?:\\]\\])?`?\\s*\\|\\s*(.*?)\\s*\\|\\s*$");
		static final Pattern ASSOC = Pattern.compile("(CRSF_FRAMETYPE_[A-Z0-9_]+)");

		Crsf(Path samples) throws IOException {
			this.samples = samples;
			h = CHeader.parse(samples.resolve("crsf").resolve("crsf_protocol.h"));
			Path wikiTable = samples.resolve("crsf.wiki").resolve("Packet-Types.md");
			if (Files.exists(wikiTable))
				for (String line : Files.readAllLines(wikiTable, StandardCharsets.UTF_8)) {
					Matcher m = WIKI_ROW.matcher(line.trim());
					if (!m.matches()) continue;
					WikiRow r = new WikiRow();
					r.id = Integer.parseInt(m.group(1), 16);
					r.extended = !m.group(2).isEmpty();
					r.telemetry = !m.group(3).isEmpty();
					r.description = m.group(5).replace("[[", "").replace("]]", "").trim();
					wiki.put(m.group(4), r);
				}
			else System.err.println("WARNING: " + wikiTable + " not found; frame docs and directions come from the header only.");
			collectFrames();
		}

		void collectFrames() {
			CEnum types = h.enumNamed("crsf_frame_type_e");
			if (types == null) throw new IllegalStateException("crsf_frame_type_e enum not found in crsf_protocol.h");
			for (Entry e : types.entries) {
				Frame f = new Frame();
				f.enumName = e.name;
				f.id = e.value;
				f.doc = e.doc;
				frames.put(e.name, f);
			}
			for (Map.Entry<String, WikiRow> w : wiki.entrySet()) {   // CRSFv3 frames documented only in the wiki
				Frame f = frames.get(w.getKey());
				if (f == null) {
					for (Frame x : frames.values()) if (x.id == w.getValue().id) f = x;   // same id, other name (RADIO_ID vs HANDSET)
				}
				if (f == null) {
					f = new Frame();
					f.enumName = w.getKey();
					f.id = w.getValue().id;
					f.doc = "(documented in the crsf-wg wiki only, not in crsf_protocol.h)";
					frames.put(w.getKey(), f);
				}
				f.telemetry = w.getValue().telemetry;
				f.extended = w.getValue().extended;
				f.doc = (w.getValue().description + (f.doc.isEmpty() ? "" : "\n" + f.doc)).trim();
			}
			for (Frame f : frames.values()) {
				f.packName = ident(f.enumName.replace("CRSF_FRAMETYPE_", ""));
				if (0x28 <= f.id) f.extended = true;
			}
			// payloads: (1) header struct announced by a `// CRSF_FRAMETYPE_X` comment right above it
			for (CStruct s : h.structs) {
				Matcher m = ASSOC.matcher(s.precedingComment);
				if (m.find() && frames.containsKey(m.group(1)) && frames.get(m.group(1)).struct == null) bind(frames.get(m.group(1)), s);
			}
			// (2) structs the header does not annotate (see STRUCT_BY_FRAME), (3) wiki-only payload tables
			for (String[] row : STRUCT_BY_FRAME) {
				Frame f = need(row[0]);
				CStruct s = h.struct(row[1]);
				if (s == null) throw new IllegalStateException("STRUCT_BY_FRAME: struct `" + row[1] + "` not found in crsf_protocol.h");
				if (f.struct == null) bind(f, s);
			}
			for (String[] row : FRAME_PREFIX_FIELDS) need(row[0]).prefix = java.util.Arrays.copyOfRange(row, 1, row.length);
			for (String[] row : FRAME_EXTRA_FIELDS) need(row[0]).extra = java.util.Arrays.copyOfRange(row, 1, row.length);
			for (String[] row : WIKI_PAYLOADS) {
				Frame f = need(row[0]);
				if (f.struct == null) f.table = java.util.Arrays.copyOfRange(row, 1, row.length);
			}
			for (Frame f : frames.values())
				if (f.struct != null && f.struct.name.startsWith("crsf_sensor_")) f.telemetry = true;
		}

		void bind(Frame f, CStruct s) {
			f.struct = s;
			framedStructs.add(s.name);
			structPack.put(s.name, f.packName);
		}

		Frame need(String enumName) {
			Frame f = frames.get(enumName);
			if (f == null) throw new IllegalStateException("Hand table refers to unknown frame `" + enumName + "` - upstream renamed or removed it");
			return f;
		}

		String emit() {
			StringBuilder sb = new StringBuilder(1 << 16);
			AdHocWriter.fileHeader(sb, "CHeaders2AdHoc", "crsf_protocol.h (ExpressLRS) + crsf-wg wiki Packet-Types.md",
					"CRSF - TBS Crossfire serial protocol as spoken between a handset, the transmitter module, the receiver and the flight controller");
			sb.append("namespace org.crsf {\n");

			// enum types that become real AdHoc enums
			for (CEnum e : h.enums) if (e.name != null && 2 <= e.entries.size()) enumTypes.put(e.name, plain(e.name));

			// Dashboard: every pack is listed without an id - the agent assigns pack ids. The CRSF frame type is not
			// a pack id, it is a protocol constant, and lives in `crsf_frame_type` and in each pack's `frame_type`.
			Map<String, Integer> ids = new TreeMap<>();
			for (Frame f : frames.values()) ids.put(f.packName, null);
			for (CStruct s : h.structs) if (!framedStructs.contains(s.name) && !isFramingStruct(s)) ids.put(plain(s.name), null);
			AdHocWriter.dashboard(sb, I1, ids);

			sb.append(I1).append("public interface CRSF {\n\n");

			defaultMaxLength(sb, I2, "A CRSF payload is 60 bytes (58 for an extended frame), but a parameter value or a tunnelled MSP frame is chunked across several frames and reassembled, so the caps are raised past AdHoc's 255-item default.");

			varintNote(sb, I2);

			// ── constants ──
			List<Define> framing = new ArrayList<>(), channel = new ArrayList<>(), msp = new ArrayList<>();
			for (Define d : h.defines) {
				if (d.value == null && d.string == null) continue;
				if (d.name.contains("CHANNEL_VALUE")) channel.add(d);
				else if (d.name.startsWith("CRSF_MSP_") || d.name.startsWith("MSP_")) msp.add(d);
				else framing.add(d);
			}
			for (CEnum e : h.enums) if (e.name == null) for (Entry x : e.entries) { Define d = new Define(); d.name = x.name; d.value = x.value; d.raw = x.literal != null ? x.literal : Long.toString(x.value); d.doc = x.doc; framing.add(d); }
			sb.append(I2).append("// ═════════════════════════ framing constants (#define / anonymous enum) ═════════════════════════\n\n");
			constants(sb, I2, "Framing", "Frame layout: [sync] [len] [type] [payload] [crc8], max 64 bytes; extended frames (type >= 0x28) add [dest] [src] in front of the payload.", framing);
			constants(sb, I2, "ChannelValue", "CRSF channel value scale (172 = 988us, 992 = 1500us, 1811 = 2012us) and the matching microsecond limits.", channel);
			constants(sb, I2, "MspTunnel", "Sizes of MSP frames tunnelled through CRSF_FRAMETYPE_MSP_REQ / MSP_RESP / MSP_WRITE.", msp);

			// ── enums ──
			sb.append(I2).append("// ═════════════════════════ enums ═════════════════════════\n\n");
			for (CEnum e : h.enums) {
				if (e.name == null) continue;
				if (e.name.equals("crsf_frame_type_e")) {
					doc(sb, I2, "Frame type byte; frames documented only in the crsf-wg wiki are appended after the header's entries.");
					sb.append(I2).append("enum ").append(plain(e.name)).append(" {\n");
					for (Frame f : frames.values()) {
						doc(sb, I3, f.doc);
						sb.append(I3).append(ident(f.enumName)).append(" = 0x").append(Long.toHexString(f.id).toUpperCase()).append(",\n");
					}
					sb.append(I2).append("}\n\n");
				} else cEnum(sb, I2, plain(e.name), e);
			}

			// ── frame packs ──
			sb.append(I2).append("// ═════════════════════════ frames (one pack per CRSF_FRAMETYPE) ═════════════════════════\n\n");
			for (Frame f : frames.values()) {
				StringBuilder d = new StringBuilder(f.doc);
				if (f.struct != null) d.append("\nC struct ").append(f.struct.name).append(" (crsf_protocol.h)").append(f.struct.doc.isEmpty() ? "" : ": " + f.struct.doc);
				if (f.table != null) d.append("\nPayload transcribed from the crsf-wg wiki page ").append(f.enumName).append(".md");
				if (f.struct == null && f.table == null) d.append("\nPayload layout not available in the header or the wiki table: carried as raw bytes.");
				doc(sb, I2, d.toString());
				if (f.extended) sb.append(I2).append("[ExtendedHeader]\n");
				sb.append(I2).append("class ").append(f.packName);
				if (f.struct != null && f.struct.base != null) sb.append(" : ").append(packOf(f.struct.base));
				sb.append(" {\n");
				sourceId(sb, I3, "frame_type", "0x" + Long.toHexString(f.id).toUpperCase(), "CRSF frame type byte - the source protocol's identity, not this pack's AdHoc id.");
				if (f.prefix != null) for (String s : f.prefix) specField(sb, I3, s, f.packName);
				if (f.struct != null) for (CField x : f.struct.fields) { doc(sb, I3, x.doc); physics(sb, I3, f.packName, x.name); sb.append(I3).append(field(x, enumTypes)).append('\n'); }
				else if (f.table != null) for (String s : f.table) specField(sb, I3, s, f.packName);
				else sb.append(I3).append("[D(60)] byte[,,] payload;\n");
				if (f.extra != null) for (String s : f.extra) specField(sb, I3, s, f.packName);
				sb.append(I2).append("}\n\n");
			}

			// ── other structs (MSP-over-CRSF payloads etc.) ──
			sb.append(I2).append("// ═════════════════════════ other structs of crsf_protocol.h ═════════════════════════\n\n");
			for (CStruct s : h.structs) {
				if (framedStructs.contains(s.name) || isFramingStruct(s)) continue;
				doc(sb, I2, (s.doc + "\nC struct " + s.name).trim());
				sb.append(I2).append("class ").append(plain(s.name));
				if (s.base != null) sb.append(" : ").append(packOf(s.base));
				sb.append(" {\n");
				for (CField x : s.fields) { doc(sb, I3, x.doc); physics(sb, I3, plain(s.name), x.name); sb.append(I3).append(field(x, enumTypes)).append('\n'); }
				sb.append(I2).append("}\n\n");
			}

			// ── topology ──
			sb.append(I2).append("// ═════════════════════════ topology ═════════════════════════\n\n");
			sb.append(I2).append("// The RF link between transmitter module and receiver is transparent to CRSF frames, so the demo\n");
			sb.append(I2).append("// models the two ends of the conversation: the handset (EdgeTX) and the flight controller.\n\n");
			AdHocWriter.host(sb, I2, "Handset", "EdgeTX / OpenTX radio with the transmitter module");
			AdHocWriter.host(sb, I2, "FlightController", "Betaflight / iNav / ArduPilot behind the receiver");
			List<String> telemetry = new ArrayList<>(), control = new ArrayList<>(), both = new ArrayList<>();
			for (Frame f : frames.values()) {
				if (f.enumName.equals("CRSF_FRAMETYPE_RC_CHANNELS_PACKED") || f.enumName.equals("CRSF_FRAMETYPE_SUBSET_RC_CHANNELS_PACKED")) control.add(f.packName);
				else if (f.telemetry) telemetry.add(f.packName);
				else both.add(f.packName);
			}
			AdHocWriter.connectionOpen(sb, I2, "CrsfLink", "Handset", "FlightController");
			sb.append(I3).append("// Telemetry items flow from the vehicle to the handset.\n");
			AdHocWriter.statePacks(sb, I3, "____________r", "Telemetry", telemetry);
			sb.append(I3).append("// Channel data flows from the handset to the vehicle.\n");
			AdHocWriter.statePacks(sb, I3, "l____________", "Control", control);
			sb.append(I3).append("// Link statistics, heartbeat and every extended-header frame (ping/info, parameters, commands, MSP tunnel) go both ways.\n");
			AdHocWriter.statePacks(sb, I3, "_____lr_____", "Extended", both);
			sb.append(I2).append("}\n\n");

			// ── attributes ──
			sb.append(I2).append("// ═════════════════════════ metadata attributes ═════════════════════════\n\n");
			AdHocWriter.attribute(sb, I2, "Bits", "Width of the C bit-field the value occupies in the packed on-the-wire struct.", "int width");
			AdHocWriter.attribute(sb, I2, "ExtendedHeader", "Extended-header frame (type >= 0x28): [dest] [src] address bytes precede the payload.");
			sb.append(I1).append("}\n");
			sb.append("}\n");
			return sb.toString();
		}

		String packOf(String cStruct) {
			String p = structPack.get(cStruct);
			return p != null ? p : plain(cStruct);
		}

		/** The two header structs describe the frame envelope, not a payload. */
		static boolean isFramingStruct(CStruct s) { return s.name.equals("crsf_header_t") || s.name.equals("crsf_ext_header_t"); }
	}

	// ── CRSF hand tables (sources: crsf_protocol.h comments and the crsf-wg wiki pages named in each row) ──

	/** Frame → header struct, for structs the header does not announce with a `// CRSF_FRAMETYPE_X` comment. */
	static final String[][] STRUCT_BY_FRAME = {
			{"CRSF_FRAMETYPE_RC_CHANNELS_PACKED", "crsf_channels_t"},       // wiki CRSF_FRAMETYPE_RC_CHANNELS_PACKED.md shows this struct
			{"CRSF_FRAMETYPE_LINK_STATISTICS", "crsfLinkStatistics_t"},     // header block comment "0x14 Link statistics"
			{"CRSF_FRAMETYPE_DEVICE_INFO", "deviceInformationPacket_t"},    // wiki CRSF_FRAMETYPE_DEVICE_INFO.md, after the display name
			{"CRSF_FRAMETYPE_HANDSET", "crsf_sync_packet_t"},               // wiki CRSF_FRAMETYPE_RADIO_ID.md: subtype 0x10 OPENTX_SYNC
	};

	/** Fields that precede the struct fields on the wire (wiki page in the comment). */
	static final String[][] FRAME_PREFIX_FIELDS = {
			{"CRSF_FRAMETYPE_DEVICE_INFO", "string(16) displayName // null-terminated device name; EdgeTX/ExpressLRS limit it to 15 characters (wiki CRSF_FRAMETYPE_DEVICE_INFO.md)"},
	};

	/** Fields that follow the struct fields on the wire. */
	static final String[][] FRAME_EXTRA_FIELDS = {
			{"CRSF_FRAMETYPE_RC_CHANNELS_PACKED", "byte? armStatus // ExpressLRS >= 4.0 / EdgeTX 2.11: optional trailing byte, 0 = disarmed, 1 = armed; absent in the 22-byte legacy frame (wiki CRSF_FRAMETYPE_RC_CHANNELS_PACKED.md)"},
	};

	/** Payloads of frames that have no struct in crsf_protocol.h, transcribed from the crsf-wg wiki page of the same name. */
	static final String[][] WIKI_PAYLOADS = {
			{"CRSF_FRAMETYPE_DEVICE_PING"}, // empty payload: only the extended dest/src addresses
			{"CRSF_FRAMETYPE_HEARTBEAT", "ushort originDeviceAddress // CRSFv3: address of the device that is alive, big-endian"},
			{"CRSF_FRAMETYPE_PARAMETER_READ", "byte fieldIndex // 0 is the root folder listing the top-level items, real fields start at 1", "byte chunkIndex // chunk of the parameter description to fetch, starting at 0"},
			{"CRSF_FRAMETYPE_PARAMETER_WRITE", "byte fieldIndex", "byte[,,56] value // encoded like the value in PARAMETER_SETTINGS_ENTRY; size depends on the parameter type"},
			{"CRSF_FRAMETYPE_PARAMETER_SETTINGS_ENTRY", "byte fieldIndex", "byte chunksRemaining // chunks still to come after this one", "byte parent // field index of the parent folder, 0 for top level", "byte typeAndHidden // low 7 bits: crsf_value_type; high bit: hidden", "string(56) label // parameter name, null-terminated", "byte[,,56] value // current/min/max/units for numeric types, index+options for SELECT, etc."},
			{"CRSF_FRAMETYPE_COMMAND", "byte realm // crsf_command: 0x10 receiver, 0x0A general", "byte subcommand // crsf_subcommand", "byte[,,54] payload", "byte crc8ba // second CRC, poly 0xBA, over type..payload; transitional parsers accept its absence"},
			{"CRSF_FRAMETYPE_ELRS_STATUS", "byte packetsBad // failed-CRC packets in the past second", "ushort packetsGood // good packets in the past second", "byte flags // 0x01 connected, 0x04 model mismatch, 0x08 armed", "string(52) message // display message for the Lua"},
			{"CRSF_FRAMETYPE_MSP_REQ", "ushort flags // MSPv2 flags: version, begin/end of frame, error", "ushort function // MSP command id", "ushort payloadLength", "byte[,,52] payload"},
			{"CRSF_FRAMETYPE_MSP_RESP", "ushort flags", "ushort function", "ushort payloadLength", "byte[,,52] payload // 58-byte chunks"},
			{"CRSF_FRAMETYPE_MSP_WRITE", "ushort flags", "ushort function", "ushort payloadLength", "byte[,,52] payload // 8-byte chunks (OpenTX outbound telemetry buffer limit)"},
			{"CRSF_FRAMETYPE_SUBSET_RC_CHANNELS_PACKED", "byte configByte // bits 0-4 first channel number, bits 5-6 resolution (0=10, 1=11, 2=12, 3=13 bits/channel), bit 7 reserved", "byte[,,59] channelData // channel values packed at the declared resolution, count implied by the frame length"},
			{"CRSF_FRAMETYPE_LINK_RX_ID", "byte rssiDbm // downlink RSSI, dBm * -1", "byte rssiPercent", "byte linkQuality", "sbyte snr", "byte rfPowerIndex"},
			{"CRSF_FRAMETYPE_LINK_TX_ID", "byte rssiDbm // uplink RSSI, dBm * -1", "byte rssiPercent", "byte linkQuality // 0 may signal a disconnected state", "sbyte snr", "byte rfPower // not currently populated", "byte packetRateFps10 // packet rate / 10, e.g. 33 for 333Hz"},
			{"CRSF_FRAMETYPE_DISPLAYPORT_CMD", "byte subcommand // 1 UPDATE, 2 CLEAR, 3 OPEN, 4 CLOSE, 5 POLL", "byte[,,57] data // UPDATE: batch id/flags (0x80 first, 0x40 last chunk), index, RLE MSP DisplayPort data"},
			{"CRSF_FRAMETYPE_KISS_REQ", "byte[,,58] payload // KISS request, layout not documented"},
			{"CRSF_FRAMETYPE_KISS_RESP", "byte[,,58] payload // KISS response, layout not documented"},
			{"CRSF_FRAMETYPE_ARDUPILOT_RESP", "byte[,,58] payload // ArduPilot passthrough, layout not documented"},
	};

	// ═══════════════════════════════════════════ MSP ═══════════════════════════════════════════

	static final class Msp {
		final CHeader v1, v2common, v2betaflight;
		final LinkedHashMap<String, Define> commands = new LinkedHashMap<>();
		final List<Define> constants = new ArrayList<>();

		Msp(Path samples) throws IOException {
			Path dir = samples.resolve("msp");
			v1 = CHeader.parse(dir.resolve("msp_protocol.h"));
			v2common = CHeader.parse(dir.resolve("msp_protocol_v2_common.h"));
			v2betaflight = CHeader.parse(dir.resolve("msp_protocol_v2_betaflight.h"));
			for (CHeader h : new CHeader[]{v1, v2common, v2betaflight})
				for (Define d : h.defines) {
					if (d.value == null && d.string == null) continue;
					if (isCommand(d)) commands.put(d.name, d);
					else constants.add(d);
				}
			for (String[] row : MSP_PAYLOADS)
				if (!commands.containsKey(row[0])) throw new IllegalStateException("MSP_PAYLOADS row `" + row[0] + "` is not defined in the MSP headers - upstream renamed or removed it");
		}

		static boolean isCommand(Define d) {
			if (d.value == null) return false;
			if (!d.name.matches("MSP2?_[A-Z0-9_]+")) return false;
			if (d.name.equals("MSP_PROTOCOL_VERSION") || d.name.equals("MSP_V2_FRAME")) return false;
			return !d.name.startsWith("MSP2_CLI_COMMAND_FLAG_") && !d.name.startsWith("MSP2TEXT_");
		}

		String emit() {
			StringBuilder sb = new StringBuilder(1 << 16);
			AdHocWriter.fileHeader(sb, "CHeaders2AdHoc", "msp_protocol.h, msp_protocol_v2_common.h, msp_protocol_v2_betaflight.h (Betaflight)",
					"MSP - MultiWii Serial Protocol between a configurator / OSD / VTX and the flight controller (every command is a request/reply RPC)");
			sb.append("namespace org.msp {\n");

			Map<String, String[]> payloads = new HashMap<>();   // "NAME/reply" or "NAME/request" → field specs
			Map<String, String> sourceOf = new HashMap<>();
			for (String[] row : MSP_PAYLOADS) {
				payloads.put(row[0] + "/" + row[1], java.util.Arrays.copyOfRange(row, 3, row.length));
				sourceOf.put(row[0] + "/" + row[1], row[2]);
			}

			// Dashboard: every pack is listed without an id - the agent assigns pack ids. The MSP command id is not
			// a pack id, it is the source protocol's identity, and lives in `MSP_COMMAND` and in each pack's `msp_id`.
			Map<String, Integer> ids = new TreeMap<>();
			for (Define c : commands.values()) {
				ids.put(c.name + "_Request", null);
				ids.put(c.name + "_Reply", null);
			}
			for (String[] row : NESTED) ids.put(row[0], null);
			AdHocWriter.dashboard(sb, I1, ids);

			sb.append(I1).append("public interface MSP {\n\n");

			defaultMaxLength(sb, I2, "An MSP v2 payload is up to 65535 bytes, so the caps are raised past AdHoc's 255-item default; a v1 payload stays within 255.");

			varintNote(sb, I2);

			sb.append(I2).append("// ═════════════════════════ constants ═════════════════════════\n\n");
			constants(sb, I2, "Constants", "Non-command #defines of the MSP headers: protocol/API version, identifier lengths, capability bits, MSP2_GET_TEXT variable ids.", constants);
			doc(sb, I2, "Framing of an MSP frame on the serial link (msp_serial.c, iNav wiki MSP-V2):\nv1: '$' 'M' dir(<|>|!) size:u8 cmd:u8 payload crc8-xor;  v2: '$' 'X' dir flags:u8 function:u16 size:u16 payload crc8-dvb-s2.\nA v2 frame may also ride inside a v1 frame with cmd = MSP_V2_FRAME (255).");
			sb.append(I2).append("public struct Framing {\n");
			sb.append(I3).append("public const string V1_PREAMBLE = \"$M\";\n");
			sb.append(I3).append("public const string V2_PREAMBLE = \"$X\";\n");
			sb.append(I3).append("public const char DIRECTION_TO_FC = '<';\n");
			sb.append(I3).append("public const char DIRECTION_FROM_FC = '>';\n");
			sb.append(I3).append("public const char DIRECTION_ERROR = '!';\n");
			sb.append(I3).append("public const long V1_MAX_PAYLOAD = 255;\n");
			sb.append(I3).append("public const long V2_MAX_PAYLOAD = 65535;\n");
			sb.append(I2).append("}\n\n");

			sb.append(I2).append("// ═════════════════════════ command ids ═════════════════════════\n\n");
			doc(sb, I2, "Every MSP / MSP2 command id defined by the Betaflight headers; v1 ids are one byte, v2 ids are 16-bit.");
			sb.append(I2).append("enum MSP_COMMAND {\n");
			for (Define c : commands.values()) {
				doc(sb, I3, c.doc);
				sb.append(I3).append(ident(c.name)).append(" = ").append(c.raw).append(",\n");
			}
			sb.append(I2).append("}\n\n");

			sb.append(I2).append("// ═════════════════════════ shared sub-packs ═════════════════════════\n\n");
			for (String[] row : NESTED) {
				doc(sb, I2, row[1]);
				sb.append(I2).append("class ").append(row[0]).append(" {\n");
				for (int i = 2; i < row.length; i++) specField(sb, I3, row[i]);
				sb.append(I2).append("}\n\n");
			}

			sb.append(I2).append("// ═════════════════════════ request / reply packs, one pair per command ═════════════════════════\n\n");
			for (Define c : commands.values()) {
				for (String side : new String[]{"Request", "Reply"}) {
					String key = c.name + "/" + side.toLowerCase();
					String[] specs = payloads.get(key);
					StringBuilder d = new StringBuilder();
					if (side.equals("Request")) d.append("Request of ").append(c.name).append(c.doc.isEmpty() ? "" : ": " + c.doc);
					else d.append("Reply to ").append(c.name).append(c.doc.isEmpty() ? "" : ": " + c.doc);
					if (specs != null) d.append("\nLayout transcribed from ").append(sourceOf.get(key)).append(" in msp.c");
					else if (side.equals("Reply") && c.doc.contains("out message")) d.append("\nLayout lives only in msp.c and is not modelled: carried as raw bytes.");
					else if (side.equals("Request") && c.doc.contains("in message")) d.append("\nLayout lives only in msp.c and is not modelled: carried as raw bytes.");
					doc(sb, I2, d.toString());
					sb.append(I2).append("class ").append(c.name).append('_').append(side).append(" {\n");
					sourceId(sb, I3, "msp_id", c.raw, "MSP command id - the source protocol's identity, not this pack's AdHoc id.");
					if (specs != null) for (String s : specs) specField(sb, I3, s, c.name + "_" + side);
					else if (side.equals("Reply") ? !c.doc.contains("in message") : c.doc.contains("in message"))
						sb.append(I3).append("[D(65_535)] byte[,,] payload;\n");
					sb.append(I2).append("}\n\n");
				}
			}

			sb.append(I2).append("// ═════════════════════════ topology ═════════════════════════\n\n");
			AdHocWriter.host(sb, I2, "Configurator", "Betaflight Configurator, OSD, VTX or any MSP client");
			AdHocWriter.host(sb, I2, "FlightController", "Betaflight / iNav flight controller");
			AdHocWriter.connectionOpen(sb, I2, "MspLink", "Configurator", "FlightController");
			sb.append(I3).append("// One RPC per MSP command: the configurator calls, the flight controller answers.\n");
			for (Define c : commands.values()) {
				doc(sb, I3, c.doc);
				sb.append(I3).append("(L____________, ").append(c.name).append("_Reply) ").append(ident(c.name)).append("(").append(c.name).append("_Request req);\n");
			}
			sb.append(I2).append("}\n");
			sb.append(I1).append("}\n");
			sb.append("}\n");
			return sb.toString();
		}
	}

	// ── MSP hand tables: layouts that exist only as sbufWrite/sbufRead sequences in msp.c (Betaflight master) ──

	/** Nested packs shared by several payloads: {name, doc, field specs…}. */
	static final String[][] NESTED = {
			{"PidGains", "One P/I/D triple of MSP_PID / MSP_SET_PID, one per axis in pidIndex_e order (roll, pitch, yaw, level, mag).",
					"byte p", "byte i", "byte d"},
	};

	/** {command, "reply" | "request", msp.c function the layout was read from, field specs…}. */
	static final String[][] MSP_PAYLOADS = {
			{"MSP_API_VERSION", "reply", "mspCommonProcessOutCommand",
					"byte protocolVersion // MSP_PROTOCOL_VERSION", "byte apiMajor // API_VERSION_MAJOR", "byte apiMinor // API_VERSION_MINOR"},
			{"MSP_FC_VARIANT", "reply", "mspCommonProcessOutCommand",
					"string(4) identifier // FLIGHT_CONTROLLER_IDENTIFIER_LENGTH chars, e.g. BTFL"},
			{"MSP_FC_VERSION", "reply", "mspCommonProcessOutCommand",
					"byte yearSince2000 // FC_VERSION_YEAR - FC_CALVER_BASE_YEAR", "byte month", "byte patchLevel", "string(255) versionString // length-prefixed string, omitted for HD VTX ports"},
			{"MSP_BOARD_INFO", "reply", "mspCommonProcessOutCommand",
					"string(4) boardIdentifier // BOARD_IDENTIFIER_LENGTH chars", "ushort hardwareRevision", "byte boardType // 0 = FC, 2 = FC with MAX7456",
					"byte targetCapabilities // bit0 VCP, bit1 softserial, bit3 flash bootloader, bit6 RX bind", "string(255) targetName", "string(255) boardName", "string(255) manufacturerId",
					"byte[32] signature // SIGNATURE_LENGTH", "byte mcuTypeId", "byte configurationState // API 1.42", "ushort gyroSampleRateHz // API 1.43",
					"uint configurationProblems // bit0 acc needs calibration, bit1 motor protocol disabled", "byte spiDeviceCount // API 1.44", "byte i2cDeviceCount // API 1.44"},
			{"MSP_STATUS", "reply", "mspProcessOutCommand",
					"ushort pidLoopTimeUs", "ushort i2cErrorCount", "ushort sensorFlags // bit0 acc, bit1 baro, bit2 mag, bit3 gps, bit4 rangefinder, bit5 gyro, bit6 optical flow, bit7 pitot",
					"uint flightModeFlags // first 32 box bits", "byte pidProfileIndex", "ushort cpuLoadPercent", "ushort gyroCycleTime // always 0 now",
					"byte extraFlightModeFlagsCount // number of flag bytes that follow", "byte[,,15] extraFlightModeFlags", "byte armingDisableFlagsCount", "uint armingDisableFlags",
					"byte configStateFlags // bit0 reboot required", "ushort cpuTemperatureC // API 1.46", "byte controlRateProfileCount", "byte batteryProfileCount // API 1.48", "byte currentBatteryProfileIndex"},
			{"MSP_RAW_IMU", "reply", "mspProcessOutCommand",
					"short[3] acc // accelerometer ADC, x y z", "short[3] gyro // gyro rate, deg/s", "short[3] mag // magnetometer ADC"},
			{"MSP_ATTITUDE", "reply", "mspProcessOutCommand",
					"short rollDecidegrees", "short pitchDecidegrees", "short yawDegrees"},
			{"MSP_ALTITUDE", "reply", "mspProcessOutCommand",
					"int altitudeCm // estimated altitude", "short varioCmPerS // estimated vertical speed"},
			{"MSP_ANALOG", "reply", "mspCommonProcessOutCommand",
					"byte legacyVoltageDecivolts // 0.1V steps, clamped to 255", "ushort mAhDrawn", "ushort rssi", "short amperageCentiamps // 0.01A steps", "ushort voltageCentivolts // 0.01V steps"},
			{"MSP_RC", "reply", "mspProcessOutCommand",
					"ushort[,,18] channels // one value per RX channel, up to MAX_SUPPORTED_RC_CHANNEL_COUNT"},
			{"MSP_RAW_GPS", "reply", "mspProcessOutCommand",
					"byte fixType", "byte numSat", "int latitudeDegE7", "int longitudeDegE7", "ushort altitudeM // 1m per lsb for backwards compatibility", "ushort groundSpeedCmPerS", "ushort groundCourseDecidegrees", "ushort pdop // API 1.44"},
			{"MSP_COMP_GPS", "reply", "mspProcessOutCommand",
					"ushort distanceToHomeM", "ushort directionToHomeDegrees", "byte gpsUpdate"},
			{"MSP_MOTOR", "reply", "mspProcessOutCommand",
					"ushort[8] motor // external motor value per output, 0 when the motor is disabled"},
			{"MSP_SET_RAW_RC", "request", "mspProcessInCommand",
					"ushort[,,18] channels // channel count derived from the payload size, up to MAX_SUPPORTED_RC_CHANNEL_COUNT"},
			{"MSP_SET_MOTOR", "request", "mspProcessInCommand",
					"ushort[,,8] motor // one external motor value per motor"},
			{"MSP_BATTERY_STATE", "reply", "mspCommonProcessOutCommand",
					"byte cellCount // 0 = battery not detected", "ushort capacityMah", "byte legacyVoltageDecivolts", "ushort mAhDrawn", "short amperageCentiamps", "byte batteryState // batteryState_e", "ushort voltageCentivolts"},
			{"MSP_RC_TUNING", "reply", "mspProcessOutCommand",
					"byte rcRateRoll", "byte rcExpoRoll", "byte[3] rates // roll, pitch, yaw", "byte tpaRateLegacy // always 0", "byte throttleMid", "byte throttleExpo", "ushort tpaBreakpointLegacy // always 0",
					"byte rcExpoYaw", "byte rcRateYaw", "byte rcRatePitch", "byte rcExpoPitch", "byte throttleLimitType // API 1.41", "byte throttleLimitPercent",
					"ushort rateLimitRoll // API 1.42", "ushort rateLimitPitch", "ushort rateLimitYaw", "byte ratesType // API 1.43", "byte throttleHover // API 1.47"},
			{"MSP_PID", "reply", "mspProcessOutCommand",
					"PidGains[5] pid // PID_ITEM_COUNT triples"},
			{"MSP_SET_PID", "request", "mspProcessInCommand",
					"PidGains[5] pid // PID_ITEM_COUNT triples"},
	};
}
