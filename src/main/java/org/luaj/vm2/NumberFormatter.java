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
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Formats a double like the C <code>printf</code> conversions <code>%f</code>,
 * <code>%e</code> and <code>%g</code>, and converts a number to a string the
 * way the reference Lua does (<code>"%.14g"</code>).
 * <p>
 * The digits come from the exact binary value of the double, rounded half-even
 * with {@link BigDecimal}, so the result is the same on every platform. It does
 * not depend on <code>Double.toString</code> or <code>String.format</code>,
 * whose output may differ between the JVM, JavaScript and C editions.
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
		String s = new BigDecimal(a).setScale(prec, RoundingMode.HALF_EVEN).toPlainString();
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
			s = new BigDecimal(a).setScale(Math.max(p-1-e[0], 0), RoundingMode.HALF_EVEN).toPlainString();
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

	/** decimal digits (prec+1 of them) and decimal exponent of a, correctly rounded */
	private static String digits(double a, int prec, int[] exp) {
		StringBuilder sb = new StringBuilder();
		if (a == 0) {
			exp[0] = 0;
		} else {
			BigDecimal bd = new BigDecimal(a).round(new MathContext(prec+1, RoundingMode.HALF_EVEN));
			sb.append(bd.unscaledValue().toString());
			exp[0] = bd.precision()-bd.scale()-1;
		}
		while (sb.length() < prec+1)
			sb.append('0');
		return sb.toString();
	}
}
