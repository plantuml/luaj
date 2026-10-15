package org.luaj.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * NumberFormatter rounds the digits itself. The reference here is the JVM's own BigDecimal,
 * which is right (TeaVM's round(MathContext) is not: it was the cause of these cases).
 */
class NumberFormatterTest {

	@Test
	void casesThatTeaVmBigDecimalGotWrong() {
		assertEquals("2e+00", NumberFormatter.exponent(1.5, 0, false));
		assertEquals("1.000000000000000e+16", NumberFormatter.exponent(1e16, 15, false));
		assertEquals("9.999999999999999e+22", NumberFormatter.exponent(1e23, 15, false));
		assertEquals("9.9999999999999992e+22", NumberFormatter.general(1e23, 17, false));
	}

	@Test
	void ties() {
		assertEquals("0", NumberFormatter.fixed(0.5, 0, false));
		assertEquals("2", NumberFormatter.fixed(1.5, 0, false));
		assertEquals("2", NumberFormatter.fixed(2.5, 0, false));
		assertEquals("4", NumberFormatter.fixed(3.5, 0, false));
		assertEquals("0.12", NumberFormatter.fixed(0.125, 2, false));
		assertEquals("0.38", NumberFormatter.fixed(0.375, 2, false));
		assertEquals("10", NumberFormatter.fixed(9.5, 0, false)); // 9.5 is a tie, 9 is odd: goes up
		assertEquals("1e+01", NumberFormatter.exponent(9.5, 0, false));
		assertEquals("1.0e+01", NumberFormatter.exponent(9.96, 1, false));
	}

	private static String refFixed(double a, int prec) {
		return new BigDecimal(a).setScale(prec, RoundingMode.HALF_EVEN).toPlainString();
	}

	private static String refExponent(double a, int prec) {
		if (a == 0)
			return refFixed(0, prec).replace("0.", "0.") + "e+00";
		BigDecimal bd = new BigDecimal(a).round(new MathContext(prec + 1, RoundingMode.HALF_EVEN));
		String d = bd.unscaledValue().toString();
		int e = bd.precision() - bd.scale() - 1;
		StringBuilder sb = new StringBuilder(d);
		while (sb.length() < prec + 1)
			sb.append('0');
		String m = sb.substring(0, 1) + (prec > 0? "." + sb.substring(1): "");
		return m + "e" + (e < 0? "-": "+") + (Math.abs(e) < 10? "0": "") + Math.abs(e);
	}

	@Test
	void randomValuesAgreeWithTheJvmBigDecimal() {
		Random r = new Random(12345);
		for (int i = 0; i < 20000; i++) {
			double a;
			switch (i % 4) {
			case 0: a = Double.longBitsToDouble(r.nextLong() & 0x7fffffffffffffffL); break; // any bit pattern
			case 1: a = r.nextInt(100000) / 8.0; break; // lots of exact ties
			case 2: a = r.nextDouble() * Math.pow(10, r.nextInt(40) - 20); break;
			default: a = (double) (r.nextLong() >>> (1 + r.nextInt(63))); break;
			}
			if (Double.isNaN(a) || Double.isInfinite(a))
				continue;
			int prec = r.nextInt(18);
			if (a < 1e30)
				assertEquals(refFixed(a, prec), NumberFormatter.fixed(a, prec, false), "fixed " + a + " " + prec);
			assertEquals(refExponent(a, prec), NumberFormatter.exponent(a, prec, false), "exponent " + a + " " + prec);
		}
	}
}
