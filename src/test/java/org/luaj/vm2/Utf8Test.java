package org.luaj.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaEngine;

/** The utf8 library and the \\u{XXX} escape (Lua 5.3 semantics). */
class Utf8Test {

	private static String eval(String code) {
		return LuaEngine.newGlobals().load(code, "t").call().tojstring();
	}

	/** the values of the expressions, separated by spaces */
	private static void checkAll(String expected, String expressions) {
		assertEquals(expected, eval("local t = table.pack(" + expressions + ") for i = 1, t.n do t[i] = tostring(t[i]) end "
				+ "return table.concat(t, ' ')"), expressions);
	}

	private static void checkError(String message, String code) {
		assertEquals(message, eval("local ok, e = pcall(function() " + code + " end) return tostring(e):gsub('^[^:]*:%d+: ', '')"),
				code);
	}

	private static String bytes(String expr) {
		return "(function(...) local s = ... return table.concat({s:byte(1, -1)}, ',') end)(" + expr + ")";
	}

	@Test
	void escape() {
		checkAll("65 195,169 226,130,172 240,159,152,128",
				"('\\u{41}'):byte(), " + bytes("'\\u{E9}'") + ", " + bytes("'\\u{20AC}'") + ", " + bytes("'\\u{1F600}'"));
		checkAll("253,191,191,191,191,191 4 0", "" + bytes("'\\u{7FFFFFFF}'") + ", #'\\u{10FFFF}', ('\\u{0}'):byte()");
		checkAll("aéb", "'a\\u{e9}b'");
	}

	@Test
	void escapeErrors() {
		checkAll("true true true true", loadFails("\\\\u41", "missing '{'") + ", " + loadFails("\\\\u{41", "missing '}'") + ", "
				+ loadFails("\\\\u{}", "hexadecimal digit expected") + ", " + loadFails("\\\\u{80000000}", "UTF-8 value too large"));
	}

	private static String loadFails(String escape, String message) {
		return "(select(2, load(\"return '" + escape + "'\")):find(\"" + message + "\", 1, true) ~= nil)";
	}

	@Test
	void char_() {
		checkAll("A 195,169 226,130,172 0 true", "utf8.char(65), " + bytes("utf8.char(233)") + ", " + bytes("utf8.char(0x20AC)")
				+ ", #utf8.char(), utf8.char(72, 228, 8364) == 'H\\u{e4}\\u{20ac}'");
		checkAll("253,191,191,191,191,191", bytes("utf8.char(0x7FFFFFFF)"));
		checkError("bad argument #1 (value out of range)", "utf8.char(-1)");
	}

	@Test
	void charpattern() {
		checkAll("14 91 0 45 127 194 45 253 93 91 128 45 191 93 42", "#utf8.charpattern, utf8.charpattern:byte(1, -1)");
	}

	@Test
	void codepoint() {
		checkAll("72 228 8364", "utf8.codepoint('H\\u{e4}\\u{20ac}', 1, -1)");
		checkAll("228", "utf8.codepoint('H\\u{e4}\\u{20ac}', 2)");
		checkAll("", "utf8.codepoint('abc', 3, 2)");
		checkAll("1114111 2147483647", "utf8.codepoint('\\u{10FFFF}'), utf8.codepoint('\\u{7FFFFFFF}')");
		checkError("invalid UTF-8 code", "utf8.codepoint('\\xff')");
		checkError("invalid UTF-8 code", "utf8.codepoint('\\xC0\\x80')"); // overlong
		checkError("invalid UTF-8 code", "utf8.codepoint('\\xE2\\x82')"); // truncated
		checkError("bad argument #3 (out of range)", "utf8.codepoint('abc', 1, 4)");
		checkError("bad argument #2 (out of range)", "utf8.codepoint('abc', 0)");
	}

	@Test
	void len() {
		checkAll("3 0 2 1", "utf8.len('a\\u{e9}\\u{20ac}'), utf8.len(''), utf8.len('a\\u{e9}\\u{20ac}', 2, -1), utf8.len('abc', 3)");
		checkAll("0", "utf8.len('abc', 4)");
		checkAll("nil 2", "utf8.len('a\\xffb')");
		checkAll("nil 3", "utf8.len('a\\u{e9}', 3)");
		checkError("bad argument #2 (initial position out of string)", "utf8.len('abc', 5)");
		checkError("bad argument #3 (final position out of string)", "utf8.len('abc', 1, 4)");
	}

	@Test
	void offset() {
		String s = "'a\\u{e9}\\u{20ac}b'"; // bytes: a | c3 a9 | e2 82 ac | b  => positions 1, 2, 4, 7
		checkAll("1 2 4 7 8 nil", "utf8.offset(" + s + ", 1), utf8.offset(" + s + ", 2), utf8.offset(" + s + ", 3), utf8.offset(" + s
				+ ", 4), utf8.offset(" + s + ", 5), utf8.offset(" + s + ", 6)");
		checkAll("7 4 2 1 nil", "utf8.offset(" + s + ", -1), utf8.offset(" + s + ", -2), utf8.offset(" + s + ", -3), utf8.offset(" + s
				+ ", -4), utf8.offset(" + s + ", -5)");
		checkAll("2 2 4", "utf8.offset(" + s + ", 0, 3), utf8.offset(" + s + ", 0, 2), utf8.offset(" + s + ", 0, 6)");
		checkAll("4", "utf8.offset(" + s + ", 2, 2)");
		checkError("initial position is a continuation byte", "utf8.offset(" + s + ", 1, 3)");
		checkError("bad argument #3 (position out of range)", "utf8.offset('abc', 1, 6)");
	}

	@Test
	void codes() {
		checkAll("1:97 2:233 4:8364 7:98", "(function() local r = {} for p, c in utf8.codes('a\\u{e9}\\u{20ac}b') do r[#r+1] = p .. ':' .. c end "
				+ "return table.concat(r, ' ') end)()");
		checkAll("0", "(function() local n = 0 for _ in utf8.codes('') do n = n + 1 end return n end)()");
		checkError("invalid UTF-8 code", "for _ in utf8.codes('a\\xffb') do end");
		checkError("invalid UTF-8 code", "for _ in utf8.codes('\\u{e9}\\x80') do end");
	}

	@Test
	void lenAndStringLibRoundTrip() {
		checkAll("true", "utf8.char(utf8.codepoint('\\u{e9}\\u{20ac}\\u{1F600}', 1, -1)) == '\\u{e9}\\u{20ac}\\u{1F600}'");
	}

	@Test
	void packageLoaded() {
		checkAll("true", "package.loaded.utf8 == utf8");
	}
}
