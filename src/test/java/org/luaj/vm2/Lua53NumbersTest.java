package org.luaj.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaEngine;

/**
 * Numbers like the reference Lua 5.3, with one difference: integers have 32 bits and wrap around.
 * Covers the integer and float subtypes, converting numbers to strings ("%.14g"), tonumber,
 * string.format, the for loop, floor division and the math functions that depend on the subtype.
 */
class Lua53NumbersTest {

	private static String eval(String code) {
		return LuaEngine.newGlobals().load(code, "t").call().tojstring();
	}

	private static void check(String expected, String expression) {
		assertEquals(expected, eval("return tostring(" + expression + ")"), expression);
	}

	/** the values of an expression, with their subtype, separated by spaces */
	private static void checkAll(String expected, String expressions) {
		assertEquals(expected, eval("local t = table.pack(" + expressions + ") for i = 1, t.n do t[i] = tostring(t[i]) end "
				+ "return table.concat(t, ' ')"), expressions);
	}

	private static void checkError(String message, String code) {
		assertEquals(message, eval("local ok, e = pcall(function() " + code + " end) return tostring(e):gsub('^[^:]*:%d+: ', '')"),
				code);
	}

	@Test
	void version() {
		assertEquals("Lua 5.3", eval("return _VERSION"));
	}

	// ===================== number to string =====================

	@Test
	void numbersToStringUseFourteenSignificantDigits() {
		check("0.3", "0.1 + 0.2");
		check("0.33333333333333", "1/3");
		check("314159265.35898", "math.pi * 1e8");
		check("3.1415926535898", "math.pi");
		check("0.78539816339745", "math.atan2(1, 1)");
		check("1.4142135623731", "2^0.5");
		check("123456.789", "123456.789");
		check("-1.5e+300", "-1.5e300");
	}

	@Test
	void numbersToStringSwitchToExponentLikeC() {
		check("1e+15", "1e15");
		check("1e+14", "1e14");
		check("99999999999999.0", "99999999999999");
		check("1.2345678901234e+14", "123456789012345");
		check("1e+100", "1e100");
		check("9.007199254741e+15", "2^53");
		check("9.2233720368548e+18", "2^63");
		check("1e-05", "1e-5");
		check("0.0001", "0.0001");
		check("4.9406564584125e-324", "2^-1074");
	}

	@Test
	void floatsAlwaysLookLikeFloats() {
		check("50.0", "100/2");
		check("51.0", "255 / 5");
		check("2147483648.0", "2^31");
		check("1099511627776.0", "math.floor(2^40)");
		check("100.0", "1e2");
		check("3.0", "3.0");
		check("-3.0", "-3.0");
		check("0.0", "0.0");
		check("-0.0", "-0.0");
	}

	@Test
	void integersLookLikeIntegers() {
		check("3", "3");
		check("-3", "-3");
		check("0", "0");
		check("0", "-0");
		check("2147483647", "2147483647");
		check("-2147483648", "math.mininteger");
		check("1020", "10 .. 20");
	}

	@Test
	void nonFiniteValues() {
		check("inf", "math.huge");
		check("-inf", "-math.huge");
		check("inf", "1e300 * 1e300");
		check("nan", "0/0");
	}

	@Test
	void negativeZeroKeepsItsSign() {
		assertEquals("-0.0", eval("local a, b = 0, -1.5 return tostring(a * b)"));
		assertEquals("-inf", eval("local a, b = 0, -1.5 return tostring(1 / (a * b))"));
		assertEquals("inf", eval("local a = 0 return tostring(1 / a)"));
		assertEquals("true", eval("local a, b = 0, -1.5 return tostring(a * b == 0)"));
		assertEquals("-inf", eval("return tostring(1 / -0.0)"));
		assertEquals("inf", eval("return tostring(1 / 0.0)"));
		assertEquals("0", eval("return tostring(-0)"));
	}

	@Test
	void formatterMatchesCForPrintfConversions() {
		assertEquals("1e+15", NumberFormatter.tostring(1e15));
		assertEquals("0.1", NumberFormatter.tostring(0.1));
		assertEquals("-0", NumberFormatter.tostring(-0.0));
		assertEquals("0", NumberFormatter.tostring(0.0));
		assertEquals("12345", NumberFormatter.tostring(12345.0));
		assertEquals("1.5", NumberFormatter.general(1.5, 6, false));
		assertEquals("1.50000", NumberFormatter.general(1.5, 6, true));
		assertEquals("1.000000e+06", NumberFormatter.exponent(1e6, 6, false));
		assertEquals("2", NumberFormatter.fixed(2.5, 0, false));
		assertEquals("3.", NumberFormatter.fixed(3.0, 0, true));
	}

	// ===================== subtypes and literals =====================

	@Test
	void literalsAreIntegersOrFloats() {
		checkAll("integer integer integer integer", "math.type(1), math.type(0), math.type(-5), math.type(2147483647)");
		checkAll("float float float float", "math.type(1.0), math.type(1e2), math.type(.5), math.type(0x1p4)");
		checkAll("float float", "math.type(2147483648), math.type(-2147483648)");
		checkAll("2147483648.0 -2147483648.0", "2147483648, -2147483648");
		checkAll("-1 2147483647 0 255", "0xffffffff, 0x7fffffff, 0x100000000, 0xff");
		checkAll("16.0 0.5", "0x1p4, 0x.8");
	}

	@Test
	void mathTypeTellsTheSubtype() {
		checkAll("integer float nil nil", "math.type(1), math.type(1.5), math.type('1'), math.type({})");
		checkAll("2147483647 -2147483648", "math.maxinteger, math.mininteger");
		assertEquals("false", eval("return tostring(pcall(math.type))"));
	}

	@Test
	void anIntegerEqualsTheFloatWithTheSameValue() {
		checkAll("true true false true", "1 == 1.0, 1.0 == 1, 1 == 1.5, 2^31 == 2147483648");
		checkAll("true true", "1 < 1.5, 2.0 <= 2");
	}

	// ===================== arithmetic =====================

	@Test
	void integerArithmeticWrapsAround() {
		checkAll("-2147483648 2147483647 -2", "math.maxinteger + 1, math.mininteger - 1, math.maxinteger * 2");
		checkAll("-2147483648 -2147483648 -2147483648", "-math.mininteger, math.abs(math.mininteger), math.mininteger // -1");
		checkAll("0 0", "math.mininteger % -1, math.maxinteger * 0");
		// the same, evaluated at compile time
		checkAll("-2147483648 2147483647", "2147483647 + 1, -2147483647 - 2");
	}

	@Test
	void arithmeticWithAFloatGivesAFloat() {
		checkAll("3.0 3.0 1.5 1.0", "1 + 2.0, 2.0 + 1, 3 - 1.5, 2 * 0.5");
		checkAll("float float float", "math.type(1 + 1.0), math.type(1.0 * 2), math.type(2^2)");
		checkAll("5.0 2.0 0.5 4.0", "10 / 2, 3 / 1.5, 1 / 2, 2 ^ 2");
		checkAll("integer integer", "math.type(2 + 2), math.type(2 * 3)");
	}

	@Test
	void floorDivisionAndModulo() {
		checkAll("3 -4 -4 3", "7 // 2, -7 // 2, 7 // -2, -7 // -2");
		checkAll("1 2 -2 -1", "7 % 3, -7 % 3, 7 % -3, -7 % -3");
		checkAll("3.0 -4.0 3.0", "7.5 // 2, -7.5 // 2, 7 // 2.0");
		checkAll("1.5 0.5 -0.5", "5.5 % 2, -5.5 % 2, 5.5 % -2");
		checkAll("inf -inf", "7 // 0.0, -7 // 0.0");
		checkAll("true", "tostring(7 % 0.0) == 'nan' or tostring(7 % 0.0) == '-nan'");
		checkAll("integer float float", "math.type(7 // 2), math.type(7 // 2.0), math.type(7.0 // 2)");
		checkAll("1 7", "5 // 3 * 1, 14 // 2");
	}

	@Test
	void divisionByZeroOfIntegersIsAnError() {
		checkError("attempt to perform 'n//0'", "return 1 // 0");
		checkError("attempt to perform 'n%%0'", "return 1 % 0");
		checkError("attempt to perform 'n//0'", "local a, b = 1, 0 return a // b");
		checkError("attempt to perform 'n%%0'", "local a, b = 1, 0 return a % b");
	}

	@Test
	void stringsAreConvertedToIntegersOrFloats() {
		checkAll("11 11.0 16 12", "'10' + 1, '10.0' + 1, '0x10' + 0, '3' * '4'");
		checkAll("4.0 -2 3 2", "'2' ^ 2, -'2', '7' // '2', '8' % '3'");
		checkAll("integer float", "math.type('10' + 0), math.type('1e1' + 0)");
	}

	@Test
	void arithmeticMetamethodsReceiveTheSubtype() {
		String mt = "local t = setmetatable({}, {__add = function(a, b) return math.type(a) or math.type(b) end, "
				+ "__sub = function(a, b) return math.type(a) or math.type(b) end, "
				+ "__mul = function(a, b) return math.type(a) or math.type(b) end, "
				+ "__div = function(a, b) return math.type(a) or math.type(b) end, "
				+ "__mod = function(a, b) return math.type(a) or math.type(b) end, "
				+ "__idiv = function(a, b) return math.type(a) or math.type(b) end, "
				+ "__pow = function(a, b) return math.type(a) or math.type(b) end}) ";
		assertEquals("integer float integer float", eval(mt + "return 1 + t .. ' ' .. 1.0 + t .. ' ' .. t + 1 .. ' ' .. t + 1.0"));
		assertEquals("integer float", eval(mt + "return 1 - t .. ' ' .. 1.5 * t"));
		assertEquals("integer float", eval(mt + "return 1 / t .. ' ' .. 1.5 % t"));
		assertEquals("integer float float", eval(mt + "return 1 // t .. ' ' .. 1.5 // t .. ' ' .. t ^ 2.5"));
	}

	@Test
	void floorDivisionHasAMetamethod() {
		assertEquals("idiv", eval("local t = setmetatable({}, {__idiv = function() return 'idiv' end}) return t // 1"));
		assertEquals("idiv", eval("local t = setmetatable({}, {__idiv = function() return 'idiv' end}) return 1 // t"));
		assertEquals("false", eval("return tostring(pcall(function() return {} // 1 end))"));
	}

	// ===================== for loop =====================

	@Test
	void forLoopWithIntegers() {
		assertEquals("1 2 3", eval("local t = {} for i = 1, 3 do t[#t+1] = i end return table.concat(t, ' ')"));
		assertEquals("10 7 4 1", eval("local t = {} for i = 10, 1, -3 do t[#t+1] = i end return table.concat(t, ' ')"));
		assertEquals("", eval("local t = {} for i = 1, 0 do t[#t+1] = i end return table.concat(t, ' ')"));
		assertEquals("integer", eval("for i = 1, 3 do return math.type(i) end"));
		assertEquals("1 2", eval("local t = {} for i = 1, 2.5 do t[#t+1] = i end return table.concat(t, ' ')"));
		assertEquals("2 1", eval("local t = {} for i = 2, 0.5, -1 do t[#t+1] = i end return table.concat(t, ' ')"));
		assertEquals("1 2 3", eval("local t = {} for i = 1, '3' do t[#t+1] = i end return table.concat(t, ' ')"));
	}

	@Test
	void forLoopDoesNotOverflow() {
		assertEquals("2147483646 2147483647", eval("local t = {} for i = math.maxinteger - 1, math.maxinteger do t[#t+1] = i end "
				+ "return table.concat(t, ' ')"));
		assertEquals("-2147483648 -2147483647 -2147483646", eval("local t = {} for i = math.mininteger, math.mininteger + 2 do "
				+ "t[#t+1] = i end return table.concat(t, ' ')"));
		assertEquals("-2147483647 -2147483648", eval("local t = {} for i = math.mininteger + 1, math.mininteger, -1 do "
				+ "t[#t+1] = i end return table.concat(t, ' ')"));
		assertEquals("1", eval("local t = {} for i = 1, 10, math.maxinteger do t[#t+1] = i end return table.concat(t, ' ')"));
		assertEquals("-1", eval("local t = {} for i = -1, -10, math.mininteger do t[#t+1] = i end return table.concat(t, ' ')"));
		assertEquals("3", eval("local n = 0 for i = math.mininteger, math.maxinteger, 2147483647 do n = n + 1 end return n"));
		assertEquals("0", eval("local n = 0 for i = 1, -math.huge do n = n + 1 end return n"));
		assertEquals("2", eval("local n = 0 for i = math.maxinteger - 1, math.huge do n = n + 1 end return n"));
		assertEquals("0", eval("local n = 0 for i = 1, 0/0 do n = n + 1 end return n"));
	}

	@Test
	void forLoopWithFloats() {
		assertEquals("1.0 2.0 3.0", eval("local t = {} for i = 1.0, 3 do t[#t+1] = tostring(i) end return table.concat(t, ' ')"));
		assertEquals("1.0 1.5 2.0", eval("local t = {} for i = 1, 2, 0.5 do t[#t+1] = tostring(i) end return table.concat(t, ' ')"));
		assertEquals("float", eval("for i = 1, 2, 0.5 do return math.type(i) end"));
		assertEquals("0.0 0.1 0.2", eval("local t = {} for i = 0, 0.2, 0.1 do t[#t+1] = tostring(i) end "
				+ "return table.concat(t, ' '):gsub('0.20000000000000', '0.2'):gsub('0.30000000000000', '0.3')"));
	}

	@Test
	void forLoopErrors() {
		checkError("'for' step is zero", "for i = 1, 2, 0 do end");
		checkError("'for' step is zero", "for i = 1.0, 2, 0 do end");
		checkError("'for' initial value must be a number", "for i = {}, 2 do end");
		checkError("'for' limit must be a number", "for i = 1, {} do end");
		checkError("'for' step must be a number", "for i = 1, 2, {} do end");
	}

	// ===================== table keys =====================

	@Test
	void floatKeysWithAnIntegerValueAreIntegerKeys() {
		assertEquals("2ab", eval("local t = {} t[1.0] = 'a' t[2] = 'b' return #t .. t[1] .. t[2.0]"));
		assertEquals("a", eval("local t = {a = 1, [3] = 'a'} return t[3.0]"));
		assertEquals("integer", eval("local t = {} t[2.0] = true for k in pairs(t) do return math.type(k) end"));
		assertEquals("float", eval("local t = {} t[2.5] = true for k in pairs(t) do return math.type(k) end"));
		assertEquals("float", eval("local t = {} t[2^31] = true for k in pairs(t) do return math.type(k) end"));
		assertEquals("true", eval("local t = {} t[2^31] = true return t[2147483648]"));
		assertEquals("false", eval("local t = {} return tostring(pcall(function() t[0/0] = 1 end))"));
	}

	@Test
	void valuesKeepTheirSubtypeInTables() {
		assertEquals("integer float", eval("local t = {1, 1.0} return math.type(t[1]) .. ' ' .. math.type(t[2])"));
		assertEquals("integer float", eval("local t = {a = 1, b = 1.0} return math.type(t.a) .. ' ' .. math.type(t.b)"));
		assertEquals("integer float", eval("local t = {} t.x = 5 t.y = 5.0 return math.type(t.x) .. ' ' .. math.type(t.y)"));
		assertEquals("integer", eval("local t = {} t.x = 5.0 t.x = 5 return math.type(t.x)"));
	}

	// ===================== tonumber and conversions =====================

	@Test
	void tonumberAcceptsTheFormsOfC() {
		assertEquals("10", eval("return tostring(tonumber('  10  '))"));
		assertEquals("12", eval("return tostring(tonumber('\\t 12\\n'))"));
		assertEquals("10.0", eval("return tostring(tonumber('1e1'))"));
		assertEquals("0.5", eval("return tostring(tonumber('.5'))"));
		assertEquals("5.0", eval("return tostring(tonumber('5.'))"));
		assertEquals("5", eval("return tostring(tonumber('+5'))"));
		assertEquals("-5", eval("return tostring(tonumber('-5'))"));
		assertEquals("0", eval("return tostring(tonumber('-0'))"));
		assertEquals("16", eval("return tostring(tonumber('0x10'))"));
		assertEquals("16", eval("return tostring(tonumber('0X10'))"));
		assertEquals("-16", eval("return tostring(tonumber('-0x10'))"));
		assertEquals("16.0", eval("return tostring(tonumber('0x1p4'))"));
		assertEquals("0.5", eval("return tostring(tonumber('0x.8'))"));
		assertEquals("21.0", eval("return tostring(tonumber('0xA.8p1'))"));
		assertEquals("255", eval("return tostring(tonumber('+ff', 16))"));
		assertEquals("35", eval("return tostring(tonumber('z', 36))"));
	}

	@Test
	void tonumberRejectsWhatIsNotANumber() {
		for (String s : new String[] { "", " ", "-", "+", "0x", "0x-", "0x.", "0x1p", "0x1p+", "1e", "1e+", ".", "e5",
				"1 2", "inf", "nan", "1_0", "--5", "0x1.2.3", "10a", "1e5.5" }) {
			assertEquals("nil", eval("return tostring(tonumber('" + s + "'))"), "'" + s + "'");
		}
		assertEquals("nil", eval("return tostring(tonumber('8', 8))"));
		assertEquals("nil", eval("return tostring(tonumber('', 16))"));
		assertEquals("nil", eval("return tostring(tonumber('-', 16))"));
		assertEquals("nil", eval("return tostring(tonumber('1.5', 10))"));
	}

	@Test
	void tonumberOfTooLargeIntegersGivesAFloat() {
		checkAll("2147483647 2147483648.0 4294967296.0", "tonumber('2147483647'), tonumber('2147483648'), tonumber('4294967296')");
		checkAll("-2147483648 -2147483649.0", "tonumber('-2147483648'), tonumber('-2147483649')");
		assertEquals("1.844674407371e+19", eval("return tostring(tonumber('18446744073709551616'))"));
		assertEquals("1.2345678901235e+19", eval("return tostring(tonumber('12345678901234567890'))"));
		assertEquals("9.2233720368548e+18", eval("return tostring(tonumber('9223372036854775808'))"));
	}

	@Test
	void tonumberWithABaseGivesIntegersThatWrapAround() {
		checkAll("255 -1 -2 1295", "tonumber('ff', 16), tonumber('ffffffff', 16), tonumber('fffffffe', 16), tonumber('zz', 36)");
		checkAll("integer integer", "math.type(tonumber('10', 2)), math.type(tonumber('7fffffff', 16))");
	}

	@Test
	void hexadecimalStringsWrapAroundModulo32Bits() {
		checkAll("-1 0 1", "tonumber('0xffffffff'), tonumber('0x100000000'), tonumber('0x100000001')");
	}

	// ===================== string.format =====================

	@Test
	void formatStringConversionHonorsToStringAndAcceptsAnyValue() {
		assertEquals("obj", eval("return string.format('%s', setmetatable({}, {__tostring=function() return 'obj' end}))"));
		assertEquals("nil|true|12|1.5|2.0", eval("return string.format('%s|%s|%s|%s|%s', nil, true, 12, 1.5, 2.0)"));
		assertEquals("[   obj]", eval("return string.format('[%6s]', setmetatable({}, {__tostring=function() return 'obj' end}))"));
		assertEquals("false", eval("return tostring(pcall(string.format, '%s'))"));
	}

	@Test
	void formatWidthAndPrecisionOfStringsAndCharacters() {
		assertEquals("[   ab]", eval("return string.format('[%5s]', 'ab')"));
		assertEquals("[ab   ]", eval("return string.format('[%-5s]', 'ab')"));
		assertEquals("[abc]", eval("return string.format('[%.3s]', 'abcdef')"));
		assertEquals("[   ab]", eval("return string.format('[%5.2s]', 'abcdef')"));
		assertEquals("[]", eval("return string.format('[%.0s]', 'abc')"));
		assertEquals("[abcdef]", eval("return string.format('[%3s]', 'abcdef')"));
		assertEquals("[    A]", eval("return string.format('[%5c]', 65)"));
		assertEquals("[A    ]", eval("return string.format('[%-5c]', 65)"));
	}

	@Test
	void formatIntegerConversions() {
		assertEquals("42 3 -7", eval("return string.format('%d %d %i', 42, 3.0, -7)"));
		assertEquals("ffffffff 80000000 FF 17", eval("return string.format('%x %x %X %o', -1, math.mininteger, 255, 15)"));
		assertEquals("4294967295", eval("return string.format('%u', -1)"));
		assertEquals("false", eval("return tostring(pcall(string.format, '%d', 3.5))"));
		assertEquals("false", eval("return tostring(pcall(string.format, '%d', 2^31))"));
		assertEquals("3.5 2.0", eval("return string.format('%.1f %.1f', 3.5, 2)"));
	}

	// ===================== math library =====================

	@Test
	void floorAndCeilGiveIntegersWhenTheyFit() {
		checkAll("3 4 -4 -3", "math.floor(3.7), math.ceil(3.2), math.floor(-3.5), math.ceil(-3.5)");
		checkAll("integer integer integer", "math.type(math.floor(2.0)), math.type(math.ceil(2.5)), math.type(math.floor(7))");
		checkAll("1e+100 float 2147483648.0", "math.floor(1e100), math.type(math.ceil(2^40)), math.floor(2^31)");
		checkAll("-2147483648", "math.floor(-2^31)");
	}

	@Test
	void absMaxMinKeepTheSubtype() {
		checkAll("3 3.5 integer float", "math.abs(-3), math.abs(-3.5), math.type(math.abs(-3)), math.type(math.abs(-3.5))");
		checkAll("2.5 3 integer", "math.max(1, 2.5), math.max(3, 2.5), math.type(math.max(3, 2.5))");
		checkAll("1 2.5 integer float", "math.min(1, 2.5), math.min(5, 2.5), math.type(math.min(1, 2.5)), math.type(math.min(5, 2.5))");
	}

	@Test
	void fmodAndModf() {
		checkAll("1 -1 1.5", "math.fmod(7, 3), math.fmod(-7, 3), math.fmod(7.5, 2)");
		checkAll("integer float", "math.type(math.fmod(7, 3)), math.type(math.fmod(7, 3.0))");
		checkAll("0", "math.fmod(math.mininteger, -1)");
		checkError("bad argument #2 (zero)", "return math.fmod(7, 0)");
		checkAll("5 0.0", "math.modf(5)");
		checkAll("3.0 0.5", "math.modf(3.5)");
		checkAll("-3.0 -0.5", "math.modf(-3.5)");
	}

	@Test
	void tointegerAndUlt() {
		checkAll("3 nil 8 nil", "math.tointeger(3.0), math.tointeger(3.5), math.tointeger('8'), math.tointeger({})");
		checkAll("nil", "math.tointeger(2^31)");
		checkAll("true false true false", "math.ult(1, -1), math.ult(-1, 1), math.ult(0, 1), math.ult(2, 2)");
	}

	@Test
	void floatFunctionsGiveFloats() {
		checkAll("2.0 float 1.0", "math.sqrt(4), math.type(math.sqrt(4)), math.cos(0)");
		checkAll("float", "math.type(math.random())");
		assertEquals("integer", eval("return math.type(math.random(10))"));
		assertEquals("integer", eval("return math.type(math.random(3, 7))"));
		assertEquals("true", eval("for i = 1, 100 do local r = math.random(3, 7) if r < 3 or r > 7 then return false end end return true"));
		assertEquals("true", eval("return math.random(-2147483648, 2147483647) ~= nil"));
	}

	// ===================== functions taking integers =====================

	@Test
	void integerArgumentsAcceptFloatsWithAnIntegerValue() {
		assertEquals("xx", eval("return string.rep('x', 2.0)"));
		assertEquals("b", eval("return (select(2.0, 'a', 'b'))"));
		assertEquals("bc", eval("return ('abc'):sub(2.0, 3.0)"));
		assertEquals("false", eval("return tostring(pcall(string.rep, 'x', 2.5))"));
		assertEquals("false", eval("return tostring(pcall(string.sub, 'abc', 1.5))"));
		assertEquals("false", eval("return tostring(pcall(string.rep, 'x', 2^31))"));
	}

	@Test
	void integerArgumentErrorMessageMentionsTheRepresentation() {
		assertEquals("true", eval("local ok, e = pcall(string.rep, 'x', 2.5) return tostring(e:find('number has no integer representation', 1, true) ~= nil)"));
	}

	// ===================== binary chunks =====================

	@Test
	void binaryChunksKeepTheSubtypeOfConstants() {
		assertEquals("integer float float integer", eval("local f = load(string.dump(load('return 1, 1.0, 2^31, 7'))) "
				+ "local a, b, c, d = f() return math.type(a) .. ' ' .. math.type(b) .. ' ' .. math.type(c) .. ' ' .. math.type(d)"));
		assertEquals("-2147483648.0 3.5 1e+100", eval("local f = load(string.dump(load('return -2147483648, 3.5, 1e100'))) "
				+ "local a, b, c = f() return a .. ' ' .. b .. ' ' .. c"));
	}
}
