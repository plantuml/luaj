package org.luaj.vm2.lib;

import java.io.IOException;

import org.luaj.vm2.LuaConstant;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * A subset of the standard Lua {@code io} library, for the engine flavor of
 * PlantUML (never for text Lua). It does not access any file by itself: every
 * file, and the three standard streams, come from the {@link LuaFileSystem} given
 * by the host.
 * <p>
 * Provided: {@code io.open}, {@code io.lines}, {@code io.read}, {@code io.write},
 * {@code io.flush}, {@code io.close}, {@code io.type}, {@code io.stdin},
 * {@code io.stdout}, {@code io.stderr}, and on a file {@code read}
 * ({@code "n"}, {@code "l"}, {@code "L"}, {@code "a"} or a byte count),
 * {@code write}, {@code lines}, {@code flush}, {@code close}.
 * <p>
 * Not provided: {@code io.popen}, {@code io.tmpfile}, {@code io.input},
 * {@code io.output}, {@code file:seek}, {@code file:setvbuf}.
 * <p>
 * Usage: {@code globals.load(new IoLib(fileSystem))}, or
 * {@link org.luaj.vm2.LuaEngine#newEngineGlobals(LuaFileSystem, OsHost)}.
 *
 * @see LuaFileSystem
 */
public class IoLib extends TwoArgFunction {

	private static final int BUFSIZE = 4096;
	/** Same limit as the reference implementation for the text of a number. */
	private static final int MAXNUMBER = 200;

	private static final int IO_OPEN = 0;
	private static final int IO_LINES = 1;
	private static final int IO_READ = 2;
	private static final int IO_WRITE = 3;
	private static final int IO_FLUSH = 4;
	private static final int IO_CLOSE = 5;
	private static final int IO_TYPE = 6;
	private static final int F_READ = 7;
	private static final int F_WRITE = 8;
	private static final int F_LINES = 9;
	private static final int F_FLUSH = 10;
	private static final int F_CLOSE = 11;
	private static final int F_TOSTRING = 12;

	private final LuaFileSystem fs;
	private LuaTable fileMeta;
	private LuaUserdata stdin;
	private LuaUserdata stdout;
	private LuaUserdata stderr;

	/**
	 * @param fs the files and standard streams of the host
	 */
	public IoLib(LuaFileSystem fs) {
		if (fs == null)
			throw new IllegalArgumentException("fs");
		this.fs = fs;
	}

	public LuaValue call(LuaValue modname, LuaValue env) {
		LuaTable methods = new LuaTable();
		methods.set("read", new IoFunction(this, F_READ, "read"));
		methods.set("write", new IoFunction(this, F_WRITE, "write"));
		methods.set("lines", new IoFunction(this, F_LINES, "lines"));
		methods.set("flush", new IoFunction(this, F_FLUSH, "flush"));
		methods.set("close", new IoFunction(this, F_CLOSE, "close"));
		fileMeta = new LuaTable();
		fileMeta.set("__index", methods);
		fileMeta.set("__name", "FILE*");
		fileMeta.set("__tostring", new IoFunction(this, F_TOSTRING, "tostring"));

		stdin = newHandle(fs.stdin(), true, false, true);
		stdout = newHandle(fs.stdout(), false, true, true);
		stderr = newHandle(fs.stderr(), false, true, true);

		LuaTable t = new LuaTable();
		t.set("open", new IoFunction(this, IO_OPEN, "open"));
		t.set("lines", new IoFunction(this, IO_LINES, "lines"));
		t.set("read", new IoFunction(this, IO_READ, "read"));
		t.set("write", new IoFunction(this, IO_WRITE, "write"));
		t.set("flush", new IoFunction(this, IO_FLUSH, "flush"));
		t.set("close", new IoFunction(this, IO_CLOSE, "close"));
		t.set("type", new IoFunction(this, IO_TYPE, "type"));
		t.set("stdin", stdin);
		t.set("stdout", stdout);
		t.set("stderr", stderr);
		env.set("io", t);
		if (!env.get("package").isnil())
			env.get("package").get("loaded").set("io", t);
		return t;
	}

	// ---------------------------------------------------------------- handles

	/** State of one Lua file object. */
	private static final class FileHandle {
		final LuaFile file;
		final boolean canRead;
		final boolean canWrite;
		final boolean standard;
		boolean closed;
		byte[] buf;
		int pos;
		int lim;

		FileHandle(LuaFile file, boolean canRead, boolean canWrite, boolean standard) {
			this.file = file;
			this.canRead = canRead;
			this.canWrite = canWrite;
			this.standard = standard;
			if (canRead)
				this.buf = new byte[BUFSIZE];
		}

		/** @return the next byte without consuming it, or -1 at the end of the file */
		int peek() throws IOException {
			if (pos >= lim) {
				int n;
				do {
					n = file.read(buf, 0, buf.length);
				} while (n == 0);
				if (n < 0)
					return -1;
				pos = 0;
				lim = n;
			}
			return buf[pos] & 0xFF;
		}

		/** @return the next byte, or -1 at the end of the file */
		int next() throws IOException {
			int c = peek();
			if (c >= 0)
				pos++;
			return c;
		}
	}

	/** A growable byte array. */
	private static final class Bytes {
		byte[] a = new byte[64];
		int n;

		void add(int b) {
			if (n == a.length)
				a = java.util.Arrays.copyOf(a, n * 2);
			a[n++] = (byte) b;
		}

		LuaString toLua() {
			return LuaString.valueOf(a, 0, n);
		}
	}

	private LuaUserdata newHandle(LuaFile f, boolean canRead, boolean canWrite, boolean standard) {
		return new LuaUserdata(new FileHandle(f, canRead, canWrite, standard), fileMeta);
	}

	private static LuaError argError(int i, String msg) {
		return new LuaError("bad argument #" + i + " (" + msg + ")");
	}

	private static FileHandle handleOf(LuaValue v) {
		if (v instanceof LuaUserdata) {
			Object o = ((LuaUserdata) v).userdata();
			if (o instanceof FileHandle)
				return (FileHandle) o;
		}
		return null;
	}

	/** The file object at argument {@code i}, which must exist and be open. */
	private static FileHandle checkOpen(Varargs args, int i) {
		LuaValue v = args.arg(i);
		FileHandle h = handleOf(v);
		if (h == null)
			throw argError(i, "FILE* expected, got " + (v.isnil() && i > args.narg() ? "no value" : v.getType().typeName));
		if (h.closed)
			throw new LuaError("attempt to use a closed file");
		return h;
	}

	private static Varargs fail(String msg) {
		return LuaValue.varargsOf(LuaConstant.NIL, LuaValue.valueOf(msg));
	}

	private static Varargs fail(IOException e) {
		String m = e.getMessage();
		return fail(m != null ? m : "input/output error");
	}

	// ------------------------------------------------------------------- open

	private Varargs open(Varargs args) {
		String path = args.checkjstring(1);
		String mode = args.optjstring(2, "r");
		int i = 0;
		boolean ok = mode.length() > 0 && "rwa".indexOf(mode.charAt(i++)) >= 0;
		boolean plus = false;
		if (ok && i < mode.length() && mode.charAt(i) == '+') {
			plus = true;
			i++;
		}
		for (; ok && i < mode.length(); i++)
			ok = mode.charAt(i) == 'b';
		if (!ok)
			throw argError(2, "invalid mode");
		char m = mode.charAt(0);
		String hostMode = plus ? m + "+" : String.valueOf(m);
		try {
			LuaFile f = fs.open(path, hostMode);
			if (f == null)
				return fail(path + ": No such file or directory");
			return newHandle(f, m == 'r' || plus, m != 'r' || plus, false);
		} catch (IOException e) {
			String msg = e.getMessage();
			return fail(path + ": " + (msg != null ? msg : "cannot open file"));
		}
	}

	// ------------------------------------------------------------------ close

	private static Varargs close(FileHandle h) {
		if (h.standard)
			return fail("cannot close standard file");
		h.closed = true;
		try {
			h.file.close();
		} catch (IOException e) {
			return fail(e);
		}
		return LuaConstant.TRUE;
	}

	// ------------------------------------------------------------------ write

	private static Varargs write(LuaValue self, FileHandle h, Varargs args, int first) {
		if (!h.canWrite)
			return fail("Bad file descriptor");
		try {
			for (int i = first; i <= args.narg(); i++) {
				LuaValue v = args.arg(i);
				if (!v.isstring())
					throw argError(i, "string expected, got " + v.getType().typeName);
				LuaString s = v.strvalue();
				int len = s.length();
				byte[] b = new byte[len];
				s.copyInto(0, b, 0, len);
				h.file.write(b, 0, len);
			}
		} catch (IOException e) {
			return fail(e);
		}
		return self;
	}

	// ------------------------------------------------------------------- read

	private static Varargs read(FileHandle h, Varargs args, int first) {
		if (!h.canRead)
			return fail("Bad file descriptor");
		int n = args.narg() - first + 1;
		try {
			if (n <= 0)
				return readLine(h, false);
			LuaValue[] out = new LuaValue[n];
			int count = 0;
			for (int i = first; i <= args.narg(); i++) {
				LuaValue r;
				LuaValue f = args.arg(i);
				if (f.getType() == org.luaj.vm2.LuaType.NUMBER) {
					r = readCount(h, f.checkint());
				} else {
					String fmt = args.checkjstring(i);
					if (fmt.startsWith("*"))
						fmt = fmt.substring(1);
					char c = fmt.length() > 0 ? fmt.charAt(0) : '?';
					switch (c) {
					case 'n':
						r = readNumber(h);
						break;
					case 'l':
						r = readLine(h, false);
						break;
					case 'L':
						r = readLine(h, true);
						break;
					case 'a':
						r = readAll(h);
						break;
					default:
						throw argError(i, "invalid format");
					}
				}
				out[count++] = r;
				if (r.isnil())
					break;
			}
			return LuaValue.varargsOf(out, 0, count);
		} catch (IOException e) {
			return fail(e);
		}
	}

	private static LuaValue readLine(FileHandle h, boolean keepNewline) throws IOException {
		Bytes b = new Bytes();
		boolean any = false;
		int c;
		while ((c = h.next()) >= 0) {
			any = true;
			if (c == '\n') {
				if (keepNewline)
					b.add(c);
				return b.toLua();
			}
			b.add(c);
		}
		return any ? b.toLua() : LuaConstant.NIL;
	}

	private static LuaValue readAll(FileHandle h) throws IOException {
		Bytes b = new Bytes();
		int c;
		while ((c = h.next()) >= 0)
			b.add(c);
		return b.toLua();
	}

	private static LuaValue readCount(FileHandle h, int n) throws IOException {
		if (n <= 0)
			return h.peek() < 0 ? LuaConstant.NIL : LuaString.valueOf("");
		Bytes b = new Bytes();
		int c;
		while (b.n < n && (c = h.next()) >= 0)
			b.add(c);
		return b.n == 0 ? LuaConstant.NIL : b.toLua();
	}

	/** Reads the text of a number like the reference implementation, then converts it. */
	private static LuaValue readNumber(FileHandle h) throws IOException {
		StringBuilder sb = new StringBuilder();
		int c = h.peek();
		while (c == ' ' || (c >= '\t' && c <= '\r')) {
			h.next();
			c = h.peek();
		}
		boolean hex = false;
		int count = 0;
		if (accept(h, sb, "-+")) {
			// sign
		}
		if (accept(h, sb, "0")) {
			if (accept(h, sb, "xX"))
				hex = true;
			else
				count = 1;
		}
		count += digits(h, sb, hex);
		if (accept(h, sb, "."))
			count += digits(h, sb, hex);
		if (count > 0 && accept(h, sb, hex ? "pP" : "eE")) {
			accept(h, sb, "-+");
			digits(h, sb, false);
		}
		return LuaString.valueOf(sb.toString()).tonumber();
	}

	private static boolean accept(FileHandle h, StringBuilder sb, String set) throws IOException {
		int c = h.peek();
		if (c >= 0 && set.indexOf(c) >= 0 && sb.length() < MAXNUMBER) {
			sb.append((char) c);
			h.next();
			return true;
		}
		return false;
	}

	private static int digits(FileHandle h, StringBuilder sb, boolean hex) throws IOException {
		int n = 0;
		while (true) {
			int c = h.peek();
			boolean d = (c >= '0' && c <= '9') || (hex && ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')));
			if (!d || sb.length() >= MAXNUMBER)
				return n;
			sb.append((char) c);
			h.next();
			n++;
		}
	}

	// ------------------------------------------------------------------ lines

	private LuaValue lines(FileHandle h, boolean closeAtEnd, Varargs formats) {
		return new LinesIterator(h, closeAtEnd, formats);
	}

	/** The iterator returned by {@code io.lines} and {@code file:lines}. */
	private static final class LinesIterator extends LibFunction {
		private final FileHandle h;
		private final boolean closeAtEnd;
		private final Varargs formats;

		LinesIterator(FileHandle h, boolean closeAtEnd, Varargs formats) {
			this.h = h;
			this.closeAtEnd = closeAtEnd;
			this.formats = formats;
		}

		public Varargs invoke(Varargs ignored) {
			if (h.closed)
				throw new LuaError("file is already closed");
			// Formats start at index 1 of "formats"; read() takes (args, first).
			Varargs r = read(h, formats, 1);
			LuaValue first = r.arg1();
			if (first.isnil()) {
				LuaValue msg = r.arg(2);
				if (!msg.isnil())
					throw new LuaError(msg.tojstring());
				if (closeAtEnd)
					close(h);
			}
			return r;
		}

		public LuaValue call() {
			return invoke(LuaConstant.NONE).arg1();
		}
	}

	// -------------------------------------------------------------- functions

	private static final class IoFunction extends org.luaj.vm2.lib.VarArgFunction {
		private final IoLib lib;
		private final int op;

		IoFunction(IoLib lib, int op, String name) {
			this.lib = lib;
			this.op = op;
			this.name = name;
		}

		public Varargs invoke(Varargs args) {
			switch (op) {
			case IO_OPEN:
				return lib.open(args);
			case IO_LINES: {
				if (args.isnoneornil(1)) {
					FileHandle in = (FileHandle) lib.stdin.userdata();
					return lib.lines(in, false, LuaConstant.NONE);
				}
				String path = args.checkjstring(1);
				Varargs opened = lib.open(LuaValue.varargsOf(LuaValue.valueOf(path), LuaValue.valueOf("r")));
				FileHandle h = handleOf(opened.arg1());
				if (h == null)
					throw new LuaError(opened.arg(2).tojstring());
				return lib.lines(h, true, args.subargs(2));
			}
			case IO_READ:
				return read((FileHandle) lib.stdin.userdata(), args, 1);
			case IO_WRITE:
				return write(lib.stdout, (FileHandle) lib.stdout.userdata(), args, 1);
			case IO_FLUSH:
				return flush(lib.stdout, (FileHandle) lib.stdout.userdata());
			case IO_CLOSE: {
				FileHandle h = args.isnoneornil(1) ? (FileHandle) lib.stdout.userdata() : checkOpen(args, 1);
				return close(h);
			}
			case IO_TYPE: {
				if (args.narg() < 1)
					throw argError(1, "value expected");
				FileHandle h = handleOf(args.arg1());
				if (h == null)
					return LuaConstant.NIL;
				return LuaValue.valueOf(h.closed ? "closed file" : "file");
			}
			case F_READ:
				return read(checkOpen(args, 1), args, 2);
			case F_WRITE:
				return write(args.arg1(), checkOpen(args, 1), args, 2);
			case F_LINES:
				return lib.lines(checkOpen(args, 1), false, args.subargs(2));
			case F_FLUSH:
				return flush(args.arg1(), checkOpen(args, 1));
			case F_CLOSE:
				return close(checkOpen(args, 1));
			case F_TOSTRING: {
				FileHandle h = handleOf(args.arg1());
				if (h == null)
					throw argError(1, "FILE* expected, got " + args.arg1().getType().typeName);
				return LuaValue.valueOf(h.closed ? "file (closed)" : "file (0x" + Integer.toHexString(System.identityHashCode(h)) + ")");
			}
			default:
				return LuaConstant.NONE;
			}
		}
	}

	private static Varargs flush(LuaValue self, FileHandle h) {
		if (!h.canWrite)
			return self;
		try {
			h.file.flush();
		} catch (IOException e) {
			return fail(e);
		}
		return self;
	}
}
