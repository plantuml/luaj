package org.luaj.vm2.lib;

import org.luaj.vm2.LuaConstant;
import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Implementation of the Lua 5.3 {@code utf8} library.
 * <p>
 * Strings are byte arrays; this library reads and writes their UTF-8
 * representation. As in Lua 5.3, the decoder is lax: it accepts code points up
 * to 0x7FFFFFFF (6-byte forms) and surrogates, but rejects overlong encodings.
 * <p>
 * Positions are byte positions, 1-based, negative values count from the end.
 */
public class Utf8Lib extends TwoArgFunction {

	/** Largest code point accepted (Lua 5.3: 2^31 - 1). */
	public static final int MAXUTF = 0x7FFFFFFF;

	private static final int[] LIMITS = { 0, 0x80, 0x800, 0x10000, 0x200000, 0x4000000 };

	public LuaValue call(LuaValue modname, LuaValue env) {
		LuaTable t = new LuaTable();
		t.set("char", new uchar());
		t.set("charpattern", LuaString.valueOf(new byte[] {
				'[', 0, '-', 0x7F, (byte) 0xC2, '-', (byte) 0xFD, ']', '[', (byte) 0x80, '-', (byte) 0xBF, ']', '*' }));
		t.set("codepoint", new codepoint());
		t.set("len", new len());
		t.set("offset", new offset());
		t.set("codes", new codes());
		env.set("utf8", t);
		if (!env.get("package").isnil())
			env.get("package").get("loaded").set("utf8", t);
		return LuaConstant.NIL;
	}

	/**
	 * Encode a code point (0..0x7FFFFFFF) as UTF-8 into buf (at least 6 bytes).
	 * @return the number of bytes written (1 to 6)
	 */
	public static int encode(int x, byte[] buf) {
		if (x < 0x80) {
			buf[0] = (byte) x;
			return 1;
		}
		int n = 1;
		int mfb = 0x3f; /* maximum that fits in first byte */
		do {
			buf[6 - n] = (byte) (0x80 | (x & 0x3f));
			n++;
			x >>>= 6;
			mfb >>= 1;
		} while (x > mfb);
		buf[6 - n] = (byte) (((~mfb << 1) | x) & 0xFF);
		/* bytes were written backwards at the end of buf: move them to the start */
		for (int i = 0; i < n; i++)
			buf[i] = buf[6 - n + i];
		return n;
	}

	/** True if s[i] exists and is a continuation byte. */
	private static boolean cont(LuaString s, int i) {
		return i >= 0 && i < s.length() && (s.luaByte(i) & 0xC0) == 0x80;
	}

	/**
	 * Decode one sequence starting at index i (0-based).
	 * @return code point in the low 31 bits and length in bits 32..34 as a long, or -1 if invalid
	 */
	static long decode(LuaString s, int i) {
		int len = s.length();
		int c = s.luaByte(i);
		if (c < 0x80)
			return (1L << 32) | c;
		long res = 0;
		int count = 0;
		while ((c & 0x40) != 0) {
			count++;
			if (i + count >= len)
				return -1;
			int cc = s.luaByte(i + count);
			if ((cc & 0xC0) != 0x80)
				return -1;
			res = (res << 6) | (cc & 0x3F);
			c <<= 1;
		}
		res |= (long) (c & 0x7F) << (count * 5);
		if (count == 0 || count > 5 || res > MAXUTF || res < LIMITS[count])
			return -1;
		return ((long) (count + 1) << 32) | res;
	}

	private static int posrelat(int pos, int len) {
		if (pos >= 0)
			return pos;
		else if (-pos > len)
			return 0;
		else
			return len + pos + 1;
	}

	static final class uchar extends VarArgFunction {
		public Varargs invoke(Varargs args) {
			int n = args.narg();
			byte[] out = new byte[n * 6];
			int pos = 0;
			byte[] tmp = new byte[6];
			for (int i = 1; i <= n; i++) {
				int c = args.checkint(i);
				if (c < 0)
					argumentError(i, "value out of range");
				int k = encode(c, tmp);
				System.arraycopy(tmp, 0, out, pos, k);
				pos += k;
			}
			return LuaString.valueOf(out, 0, pos);
		}
	}

	static final class codepoint extends VarArgFunction {
		public Varargs invoke(Varargs args) {
			LuaString s = args.checkstring(1);
			int len = s.length();
			int posi = posrelat(args.optint(2, 1), len);
			int pose = posrelat(args.optint(3, posi), len);
			if (posi < 1)
				argumentError(2, "out of range");
			if (pose > len)
				argumentError(3, "out of range");
			if (posi > pose)
				return LuaConstant.NONE;
			int i = posi - 1;
			int end = pose;
			java.util.ArrayList<LuaValue> r = new java.util.ArrayList<LuaValue>();
			while (i < end) {
				long d = decode(s, i);
				if (d < 0)
					throw new org.luaj.vm2.LuaError("invalid UTF-8 code");
				r.add(LuaInteger.valueOf((int) d));
				i += (int) (d >>> 32);
			}
			return varargsOf(r.toArray(new LuaValue[0]));
		}
	}

	static final class len extends VarArgFunction {
		public Varargs invoke(Varargs args) {
			LuaString s = args.checkstring(1);
			int len = s.length();
			int posi = posrelat(args.optint(2, 1), len);
			int posj = posrelat(args.optint(3, -1), len);
			if (!(1 <= posi && --posi <= len))
				argumentError(2, "initial position out of string");
			if (!(--posj < len))
				argumentError(3, "final position out of string");
			int n = 0;
			while (posi <= posj) {
				long d = decode(s, posi);
				if (d < 0)
					return varargsOf(LuaConstant.NIL, LuaInteger.valueOf(posi + 1));
				posi += (int) (d >>> 32);
				n++;
			}
			return LuaInteger.valueOf(n);
		}
	}

	static final class offset extends VarArgFunction {
		public Varargs invoke(Varargs args) {
			LuaString s = args.checkstring(1);
			int len = s.length();
			int n = args.checkint(2);
			int posi = (n > 0)? 1: len + 1;
			posi = posrelat(args.optint(3, posi), len);
			if (!(1 <= posi && --posi <= len))
				argumentError(3, "position out of range");
			if (n == 0) {
				while (posi > 0 && cont(s, posi))
					posi--;
			} else {
				if (cont(s, posi))
					throw new org.luaj.vm2.LuaError("initial position is a continuation byte");
				if (n < 0) {
					while (n < 0 && posi > 0) {
						do {
							posi--;
						} while (posi > 0 && cont(s, posi));
						n++;
					}
				} else {
					n--;
					while (n > 0 && posi < len) {
						do {
							posi++;
						} while (cont(s, posi));
						n--;
					}
				}
			}
			if (n == 0)
				return LuaInteger.valueOf(posi + 1);
			return LuaConstant.NIL;
		}
	}

	static final class codes extends VarArgFunction {
		static final iter ITER = new iter();
		public Varargs invoke(Varargs args) {
			LuaString s = args.checkstring(1);
			return varargsOf(ITER, s, LuaConstant.ZERO);
		}
	}

	static final class iter extends VarArgFunction {
		public Varargs invoke(Varargs args) {
			LuaString s = args.checkstring(1);
			int len = s.length();
			int n = args.checkint(2) - 1;
			if (n < 0)
				n = 0;
			else if (n < len) {
				n++;
				while (cont(s, n))
					n++;
			}
			if (n >= len)
				return LuaConstant.NIL;
			long d = decode(s, n);
			if (d < 0 || cont(s, n + (int) (d >>> 32)))
				throw new org.luaj.vm2.LuaError("invalid UTF-8 code");
			return varargsOf(LuaInteger.valueOf(n + 1), LuaInteger.valueOf((int) d));
		}
	}
}
