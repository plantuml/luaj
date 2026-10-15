package org.luaj.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Behaviors of the reference Lua 5.2 that this library must reproduce:
 * converting numbers to strings ("%.14g"), tonumber, string.format("%s").
 */
class Lua52CompatibilityTest {

	private static String eval(String code) {
		return JsePlatform.standardGlobals().load(code, "t").call().tojstring();
	}

	private static void check(String expected, String expression) {
		assertEquals(expected, eval("return tostring(" + expression + ")"), expression);
	}

	@Test
	void version() {
		assertEquals("Lua 5.2", eval("return _VERSION"));
	}

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
		check("99999999999999", "99999999999999");
		check("1.2345678901234e+14", "123456789012345");
		check("1e+100", "1e100");
		check("9.007199254741e+15", "2^53");
		check("9.2233720368548e+18", "2^63");
		check("1e-05", "1e-5");
		check("0.0001", "0.0001");
		check("4.9406564584125e-324", "2^-1074");
	}

	@Test
	void integralValuesStayIntegers() {
		check("50", "100/2");
		check("51", "255 / 5");
		check("2147483648", "2^31");
		check("1099511627776", "math.floor(2^40)");
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
		// a negative zero computed at run time (the literal -0.0 is still folded to the integer 0)
		assertEquals("-0", eval("local a, b = 0, -1.5 return tostring(a * b)"));
		assertEquals("-inf", eval("local a, b = 0, -1.5 return tostring(1 / (a * b))"));
		assertEquals("inf", eval("local a = 0 return tostring(1 / a)"));
		assertEquals("true", eval("local a, b = 0, -1.5 return tostring(a * b == 0)"));
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

	@Test
	void tonumberAcceptsTheFormsOfC() {
		assertEquals("10", eval("return tostring(tonumber('  10  '))"));
		assertEquals("12", eval("return tostring(tonumber('\\t 12\\n'))"));
		assertEquals("10", eval("return tostring(tonumber('1e1'))"));
		assertEquals("0.5", eval("return tostring(tonumber('.5'))"));
		assertEquals("5", eval("return tostring(tonumber('5.'))"));
		assertEquals("5", eval("return tostring(tonumber('+5'))"));
		assertEquals("-5", eval("return tostring(tonumber('-5'))"));
		assertEquals("16", eval("return tostring(tonumber('0x10'))"));
		assertEquals("16", eval("return tostring(tonumber('0X10'))"));
		assertEquals("-16", eval("return tostring(tonumber('-0x10'))"));
		assertEquals("16", eval("return tostring(tonumber('0x1p4'))"));
		assertEquals("0.5", eval("return tostring(tonumber('0x.8'))"));
		assertEquals("21", eval("return tostring(tonumber('0xA.8p1'))"));
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
	}

	@Test
	void tonumberOfALongIntegerFallsBackToADouble() {
		assertEquals("1.844674407371e+19", eval("return tostring(tonumber('18446744073709551616'))"));
		assertEquals("1.2345678901235e+19", eval("return tostring(tonumber('12345678901234567890'))"));
		assertEquals("9.2233720368548e+18", eval("return tostring(tonumber('9223372036854775808'))"));
	}

	@Test
	void formatStringConversionHonorsToStringAndAcceptsAnyValue() {
		assertEquals("obj", eval("return string.format('%s', setmetatable({}, {__tostring=function() return 'obj' end}))"));
		assertEquals("nil|true|12|1.5", eval("return string.format('%s|%s|%s|%s', nil, true, 12, 1.5)"));
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
}
