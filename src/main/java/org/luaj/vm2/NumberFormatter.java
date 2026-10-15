/*******************************************************************************
 * Copyright (c) 2009 Luaj.org. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 ******************************************************************************/
package org.luaj.vm2;

import java.math.BigDecimal;

/**
 * Formats a double like the C <code>printf</code> conversions <code>%f</code>,
 * <code>%e</code> and <code>%g</code>, and converts a number to a string the
 * way the reference Lua does (<code>"%.14g"</code>).
 * <p>
 * The digits come from the exact binary value of the double, rounded half-even
 * by this class, so the result is the same on every platform. It does
 * not depend on <code>Double.toString</code> or <code>String.format</code>,
 * whose output may differ between the JVM, JavaScript and C editions.
 * <p>
 * Only <code>new BigDecimal(double).toPlainString()</code> is used from
 * {@link BigDecimal}: the rounding (<code>setScale</code>, <code>round</code>,
 * <code>precision</code>) is done here on the digits, because TeaVM's
 * implementation of <code>round(MathContext)</code> is wrong for some values
 * (1.5 with one digit, 1e16 with 16 digits, 1e23 with 16 digits).
 * <p>
 * All methods take the absolute value of a finite number: the sign, the
 * padding and the special values (nan, inf) are handled by the callers.
 */
public final class NumberFormatter {

	/** Number of significant digits used to convert a number to a string (LUAI_NUMFFORMAT is "%.14g"). */
	public static final int TOSTRING_PRECISION = 14;

	private NumberFormatter() {
	}

	/** Same as the conversion <code>%.{prec}f</code>. */
	public static String fixed(double a, int prec, boolean alternateForm) {
		String s = fixedDigits(a, prec);
		return alternateForm && prec == 0? s+".": s;
	}

	/** Same as the conversion <code>%.{prec}e</code> (two digits of exponent at least). */
	public static String exponent(double a, int prec, boolean alternateForm) {
		int[] e = new int[1];
		String d = digits(a, prec, e);
		StringBuilder sb = new StringBuilder();
		sb.append(d.charAt(0));
		if (prec > 0 || alternateForm)
			sb.append('.');
		sb.append(d, 1, d.length());
		sb.append('e').append(e[0] < 0? '-': '+');
		int ae = Math.abs(e[0]);
		if (ae < 10)
			sb.append('0');
		sb.append(ae);
		return sb.toString();
	}

	/** Same as the conversion <code>%.{prec}g</code>. */
	public static String general(double a, int prec, boolean alternateForm) {
		int p = prec == 0? 1: prec;
		int[] e = new int[1];
		digits(a, p-1, e);
		String s;
		if (e[0] >= -4 && e[0] < p)
			s = fixedDigits(a, Math.max(p-1-e[0], 0));
		else
			s = exponent(a, p-1, alternateForm);
		if (alternateForm)
			return s.indexOf('.') < 0? s+".": s;
		// strip trailing zeros of the mantissa
		int ei = s.indexOf('e');
		String mant = ei < 0? s: s.substring(0, ei);
		String tail = ei < 0? "": s.substring(ei);
		if (mant.indexOf('.') >= 0) {
			int end = mant.length();
			while (mant.charAt(end-1) == '0')
				end--;
			if (mant.charAt(end-1) == '.')
				end--;
			mant = mant.substring(0, end);
		}
		return mant+tail;
	}

	/**
	 * Converts a number to a string like the reference Lua 5.2 does
	 * (<code>"%.14g"</code>): 0.1+0.2 gives 0.3, 1e15 gives 1e+15, -0.0 gives -0.
	 * The values nan, inf and -inf are written like {@link LuaDouble} always did.
	 */
	public static String tostring(double v) {
		if (v != v)
			return LuaDouble.JSTR_NAN;
		if (v == Double.POSITIVE_INFINITY)
			return LuaDouble.JSTR_POSINF;
		if (v == Double.NEGATIVE_INFINITY)
			return LuaDouble.JSTR_NEGINF;
		boolean neg = v < 0 || (v == 0 && 1/v < 0);
		double a = Math.abs(v);
		String s;
		if (a < 1e14 && a == (long) a)
			s = Long.toString((long) a); // fast path: at most 14 digits, nothing to round
		else
			s = general(a, TOSTRING_PRECISION, false);
		return neg? "-"+s: s;
	}

	/** exact decimal expansion of a (finite, not negative): digits of the integer part, then of the fraction */
	private static String[] exact(double a) {
		String s = new BigDecimal(a).toPlainString();
		int dot = s.indexOf('.');
		return dot < 0? new String[] { s, "" }: new String[] { s.substring(0, dot), s.substring(dot+1) };
	}

	/**
	 * Rounds a string of decimal digits to its first <code>keep</code> digits (half-even, on the exact
	 * value). The result has <code>keep</code> digits, or <code>keep+1</code> if the rounding carried
	 * out of the first digit (999 gives 1000). <code>keep</code> must be at least 1.
	 */
	private static String roundDigits(String digits, int keep) {
		if (digits.length() <= keep) {
			StringBuilder sb = new StringBuilder(digits);
			while (sb.length() < keep)
				sb.append('0');
			return sb.toString();
		}
		char next = digits.charAt(keep);
		boolean up;
		if (next != '5')
			up = next > '5';
		else {
			up = false;
			for (int i = keep+1; i < digits.length() && !up; i++)
				up = digits.charAt(i) != '0';
			if (!up) // exactly half: round to even
				up = ((digits.charAt(keep-1) - '0') & 1) != 0;
		}
		char[] r = digits.substring(0, keep).toCharArray();
		if (up) {
			int i = keep - 1;
			while (i >= 0 && r[i] == '9')
				r[i--] = '0';
			if (i >= 0)
				r[i]++;
			else
				return "1" + new String(r);
		}
		return new String(r);
	}

	/** a with exactly prec digits after the decimal point, rounded half-even (like %.{prec}f) */
	private static String fixedDigits(double a, int prec) {
		String[] ip = exact(a);
		String r = roundDigits(ip[0] + ip[1], ip[0].length() + prec);
		int intLen = r.length() - prec;
		return prec == 0? r: r.substring(0, intLen) + "." + r.substring(intLen);
	}

	/** decimal digits (prec+1 of them) and decimal exponent of a, correctly rounded */
	private static String digits(double a, int prec, int[] exp) {
		if (a == 0) {
			exp[0] = 0;
			StringBuilder sb = new StringBuilder();
			while (sb.length() < prec+1)
				sb.append('0');
			return sb.toString();
		}
		String[] ip = exact(a);
		String all = ip[0] + ip[1];
		int first = 0;
		while (all.charAt(first) == '0')
			first++;
		int e = ip[0].length() - first - 1;
		String r = roundDigits(all.substring(first), prec+1);
		if (r.length() > prec+1) { // carry: 9.99 gives 10.0
			r = r.substring(0, prec+1);
			e++;
		}
		exp[0] = e;
		return r;
	}
}
