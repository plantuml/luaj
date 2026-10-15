package org.luaj.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaEngine;

/** Bitwise operators on 32-bit integers: &amp; | ~ (xor) ~ (not) &lt;&lt; &gt;&gt;, as in Lua 5.3 but on 32 bits. */
class BitwiseTest {

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

	@Test
	void basicOperators() {
		checkAll("1 7 6 -6", "5 & 3, 5 | 3, 5 ~ 3, ~5");
		checkAll("15 -1 0 -2", "0xff & 0x0f, ~0, 6 & 1, ~1");
		checkAll("-1 -2147483648 2147483647", "0xffffffff & -1, 0x80000000 | 0, 0x7fffffff & -1");
	}

	@Test
	void shifts() {
		checkAll("16 16 -2147483648 0 0", "1 << 4, 256 >> 4, 1 << 31, 1 << 32, 1 >> 32");
		checkAll("0 4 15", "1 << -1, 2 >> -1, -1 >> 28");
		checkAll("1 1 0", "(1 << 31) >> 31, math.mininteger >> 31, 1 >> math.mininteger");
		checkAll("0 0", "1 << math.mininteger, 1 << math.maxinteger");
	}

	@Test
	void resultsAreIntegers() {
		checkAll("integer integer integer integer", "math.type(1 & 1), math.type(1 << 1), math.type(~1), math.type(2.0 | 0)");
	}

	@Test
	void floatsWithAnIntegerValueAndNumericStringsAreAccepted() {
		checkAll("3 3 11", "3.0 | 0, '7' & 3, '0x0a' | 1");
		checkAll("4 3", "2^2 | 0, 3.0 & 3");
	}

	@Test
	void precedence() {
		// lowest to highest: or, and, comparison, |, ~, &, shift, .., + -, * / // %, unary, ^
		checkAll("3", "1 | 2 ~ 3 & 4");
		checkAll("8 8", "1 + 1 << 2, 1 << 2 + 1");
		checkAll("2147483647 -1", "- 1 >> 1, ~1 + 1");
		checkAll("true true", "(1 | 2) == 3, (3 & 1) == 1");
		checkAll("4 1", "5 // 2 << 1, 1 << 1 >> 1");
		checkAll("34", "3 .. 4 | 0");
		checkAll("-128", "~127");
	}

	@Test
	void constantsAndVariablesGiveTheSameResults() {
		assertEquals("true", eval("local a, b, s = 12, 10, 3 "
				+ "return (a & b) == (12 & 10) and (a | b) == (12 | 10) and (a ~ b) == (12 ~ 10) and ~a == ~12 "
				+ "and (a << s) == (12 << 3) and (a >> s) == (12 >> 3)"));
		checkAll("8 14 6 -13 48 3", "12 & 10, 12 | 10, 12 ~ 10, ~12, 12 << 2, 12 >> 2");
		assertEquals("8 14 6 -13 48 3", eval("local a, b, c = 12, 10, 2 return table.concat({a & b, a | b, a ~ b, ~a, a << c, a >> c}, ' ')"));
	}

	@Test
	void errors() {
		checkError("number has no integer representation", "return 1.5 | 0");
		checkError("number has no integer representation", "local a = 2^31 return a | 0");
		checkError("number has no integer representation", "local a = 0/0 return ~a");
		checkError("attempt to perform bitwise operation on a table value", "return {} | 0");
		checkError("attempt to perform bitwise operation on a table value", "return 1 & {}");
		checkError("attempt to perform bitwise operation on a string value", "return 'a' & 1");
		checkError("attempt to perform bitwise operation on a nil value", "return ~nil");
	}

	@Test
	void metamethods() {
		String mt = "local t = setmetatable({}, {__band = function() return 'band' end, __bor = function() return 'bor' end, "
				+ "__bxor = function() return 'bxor' end, __shl = function() return 'shl' end, "
				+ "__shr = function() return 'shr' end, __bnot = function() return 'bnot' end}) ";
		assertEquals("band band bor bxor shl shr bnot", eval(mt + "return table.concat({t & 1, 1 & t, t | 1, 1 ~ t, t << 1, t >> 1, ~t}, ' ')"));
		assertEquals("2 true", eval("local t = setmetatable({}, {__band = function(a, b) return rawequal(a, b) and 2 or 0 end}) "
				+ "local r = t & t return r .. ' ' .. tostring(~(function() return 0 end)() == -1)"));
		assertEquals("true", eval("local t = setmetatable({}, {__bnot = function(a, b) return rawequal(a, b) end}) return ~t"));
	}

	@Test
	void bit32LibraryIsGone() {
		assertEquals("nil", eval("return tostring(bit32)"));
	}

	@Test
	void shiftAndBitTokensDoNotBreakComparisons() {
		checkAll("true true true false", "1 < 2, 2 > 1, 1 <= 1, 1 >= 2");
		checkAll("true", "3 ~= 4");
		assertEquals("3", eval("local t = {} t.x = 1 << 1 return t.x + 1"));
	}
}
