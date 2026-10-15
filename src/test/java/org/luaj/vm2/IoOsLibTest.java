package org.luaj.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.lib.LuaFile;
import org.luaj.vm2.lib.LuaFileSystem;
import org.luaj.vm2.lib.OsHost;

/** The {@code io} and {@code os} subsets of the engine flavor, on an in-memory host. */
class IoOsLibTest {

	/** A host with files in memory and recorded standard streams. */
	private static final class MemHost implements LuaFileSystem, OsHost {
		final Map<String, byte[]> files = new HashMap<>();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final ByteArrayOutputStream err = new ByteArrayOutputStream();
		byte[] in = new byte[0];
		int exitStatus = -1;
		boolean exitReturns = false;
		int closed;

		void put(String path, String text) {
			files.put(path, text.getBytes(StandardCharsets.UTF_8));
		}

		String get(String path) {
			return new String(files.get(path), StandardCharsets.UTF_8);
		}

		public LuaFile open(String path, String mode) throws IOException {
			byte[] content = files.get(path);
			char m = mode.charAt(0);
			if (m == 'r' && content == null)
				throw new FileNotFoundException("No such file or directory");
			boolean plus = mode.length() > 1;
			if (m == 'w' || content == null)
				content = new byte[0];
			final ByteArrayOutputStream data = new ByteArrayOutputStream();
			data.write(content);
			final boolean canRead = m == 'r' || plus;
			final boolean canWrite = m != 'r' || plus;
			final int[] readPos = { 0 };
			files.put(path, data.toByteArray());
			return new LuaFile() {
				public int read(byte[] buf, int off, int len) throws IOException {
					if (!canRead)
						throw new IOException("Bad file descriptor");
					byte[] all = files.get(path);
					if (readPos[0] >= all.length)
						return -1;
					int n = Math.min(len, all.length - readPos[0]);
					System.arraycopy(all, readPos[0], buf, off, n);
					readPos[0] += n;
					return n;
				}

				public void write(byte[] buf, int off, int len) throws IOException {
					if (!canWrite)
						throw new IOException("Bad file descriptor");
					byte[] all = files.get(path);
					byte[] n = new byte[all.length + len];
					System.arraycopy(all, 0, n, 0, all.length);
					System.arraycopy(buf, off, n, all.length, len);
					files.put(path, n);
				}

				public void flush() {
				}

				public void close() {
					closed++;
				}
			};
		}

		public LuaFile stdin() {
			final int[] pos = { 0 };
			return new LuaFile() {
				public int read(byte[] buf, int off, int len) {
					if (pos[0] >= in.length)
						return -1;
					int n = Math.min(len, in.length - pos[0]);
					System.arraycopy(in, pos[0], buf, off, n);
					pos[0] += n;
					return n;
				}

				public void write(byte[] buf, int off, int len) throws IOException {
					throw new IOException("Bad file descriptor");
				}

				public void flush() {
				}

				public void close() {
				}
			};
		}

		public LuaFile stdout() {
			return sink(out);
		}

		public LuaFile stderr() {
			return sink(err);
		}

		private LuaFile sink(final ByteArrayOutputStream s) {
			return new LuaFile() {
				public int read(byte[] buf, int off, int len) throws IOException {
					throw new IOException("Bad file descriptor");
				}

				public void write(byte[] buf, int off, int len) {
					s.write(buf, off, len);
				}

				public void flush() {
				}

				public void close() {
				}
			};
		}

		public String getenv(String name) {
			return name.equals("HOME") ? "/home/x" : null;
		}

		public double clock() {
			return 1.5;
		}

		public long time() {
			return 1700000000L;
		}

		public void exit(int status) {
			exitStatus = status;
			if (!exitReturns)
				throw new IllegalStateException("exit " + status);
		}
	}

	private static String eval(MemHost h, String code) {
		return LuaEngine.newEngineGlobals(h, h).load(code, "t").call().tojstring();
	}

	@Test
	void textLuaHasNeitherIoNorOs() {
		assertEquals("nil nil", LuaEngine.newGlobals().load("return tostring(io) .. ' ' .. tostring(os)", "t").call().tojstring());
	}

	@Test
	void engineLuaHasThemAndTheyAreInPackageLoaded() {
		MemHost h = new MemHost();
		assertEquals("table table true true", eval(h,
				"return type(io) .. ' ' .. type(os) .. ' ' .. tostring(package.loaded.io == io) .. ' ' .. tostring(package.loaded.os == os)"));
	}

	@Test
	void noPopenTmpfileSeekOrExecute() {
		MemHost h = new MemHost();
		assertEquals("nil nil nil nil nil", eval(h,
				"local f = io.open('a', 'w') return tostring(io.popen) .. ' ' .. tostring(io.tmpfile) .. ' ' .. tostring(f.seek) .. ' ' .. tostring(os.execute) .. ' ' .. tostring(os.remove)"));
	}

	@Test
	void writeThenReadLines() {
		MemHost h = new MemHost();
		assertEquals("alpha|beta||gamma|nil", eval(h,
				"local f = assert(io.open('x.txt', 'w'))\n"
						+ "f:write('alpha\\n', 'beta\\n', '\\n', 'gamma')\n"
						+ "f:close()\n"
						+ "local r = assert(io.open('x.txt', 'r'))\n"
						+ "local t = {}\n"
						+ "for i = 1, 5 do t[i] = tostring(r:read('l')) end\n"
						+ "r:close()\n"
						+ "return table.concat(t, '|')"));
		assertEquals("alpha\nbeta\n\ngamma", h.get("x.txt"));
	}

	@Test
	void readFormats() {
		MemHost h = new MemHost();
		h.put("n.txt", "  12 -3.5 0x10 abc\nrest of\nthe file");
		assertEquals("12 -3.5 16 nil", eval(h,
				"local f = io.open('n.txt')\n"
						+ "local a, b, c = f:read('n', 'n', 'n')\n"
						+ "local d = f:read('n')\n"
						+ "return a .. ' ' .. b .. ' ' .. c .. ' ' .. tostring(d)"));
		// a failed number reading consumes only what looks like a number: "abc" stays
		assertEquals("abc", eval(h,
				"local f = io.open('n.txt')\n"
						+ "f:read('n', 'n', 'n')\n"
						+ "f:read('n')\n"
						+ "return f:read('l')"));
	}

	@Test
	void readAllCountAndLineKeepingNewline() {
		MemHost h = new MemHost();
		h.put("a.txt", "one\ntwo\nthree");
		assertEquals("one\n|tw|o\nthree||nil", eval(h,
				"local f = io.open('a.txt')\n"
						+ "local l = f:read('L')\n"
						+ "local c = f:read(2)\n"
						+ "local rest = f:read('a')\n"
						+ "local empty = f:read('a')\n"
						+ "local eof = f:read(1)\n"
						+ "return l .. '|' .. c .. '|' .. rest .. '|' .. empty .. '|' .. tostring(eof)"));
		assertEquals("nil", eval(h, "local f = io.open('a.txt') f:read('a') return tostring(f:read('l'))"));
		assertEquals("true nil", eval(h,
				"local f = io.open('a.txt') local z = f:read(0) f:read('a') return tostring(z == '') .. ' ' .. tostring(f:read(0))"));
	}

	@Test
	void fileLinesAndIoLines() {
		MemHost h = new MemHost();
		h.put("l.txt", "a\nb\nc\n");
		assertEquals("a,b,c", eval(h,
				"local t = {} for l in io.lines('l.txt') do t[#t + 1] = l end return table.concat(t, ',')"));
		assertEquals("1,2,3", eval(h,
				"local f = io.open('l.txt') local t = {} for l in f:lines() do t[#t + 1] = #l + #t end return table.concat(t, ',')"));
		assertEquals(1, h.closed, "io.lines closes its file at the end, file:lines does not");
	}

	@Test
	void ioLinesFailsOnMissingFileAndOnAClosedFile() {
		MemHost h = new MemHost();
		LuaError e = assertThrows(LuaError.class, () -> eval(h, "for l in io.lines('nope.txt') do end"));
		assertTrue(e.getMessage().contains("nope.txt: No such file or directory"), e.getMessage());
		h.put("c.txt", "x\n");
		e = assertThrows(LuaError.class, () -> eval(h, "local f = io.open('c.txt') local it = f:lines() f:close() it()"));
		assertTrue(e.getMessage().contains("file is already closed"), e.getMessage());
	}

	@Test
	void openFailureReturnsNilAndMessage() {
		MemHost h = new MemHost();
		assertEquals("nil nope.txt: No such file or directory", eval(h,
				"local f, msg = io.open('nope.txt') return tostring(f) .. ' ' .. msg"));
	}

	@Test
	void invalidModeIsAnArgumentError() {
		MemHost h = new MemHost();
		LuaError e = assertThrows(LuaError.class, () -> eval(h, "io.open('x', 'z')"));
		assertTrue(e.getMessage().contains("invalid mode"), e.getMessage());
		assertEquals("nil", eval(h, "return tostring(io.open('missing', 'rb'))"));
		assertEquals("table", eval(h, "local f = io.open('y', 'w+b') return type(getmetatable(f))"));
	}

	@Test
	void appendKeepsTheExistingContent() {
		MemHost h = new MemHost();
		h.put("log.txt", "first\n");
		eval(h, "local f = io.open('log.txt', 'a') f:write('second\\n') f:close()");
		assertEquals("first\nsecond\n", h.get("log.txt"));
	}

	@Test
	void writeAcceptsNumbersAndChainsButRefusesOtherValues() {
		MemHost h = new MemHost();
		eval(h, "local f = io.open('w.txt', 'w') f:write(1, ' ', 2.5):write(' x') f:close()");
		assertEquals("1 2.5 x", h.get("w.txt"));
		LuaError e = assertThrows(LuaError.class, () -> eval(h, "io.open('w2.txt', 'w'):write({})"));
		assertTrue(e.getMessage().contains("string expected, got table"), e.getMessage());
	}

	@Test
	void readingAWriteOnlyFileAndWritingAReadOnlyFileFail() {
		MemHost h = new MemHost();
		h.put("r.txt", "x");
		assertEquals("nil Bad file descriptor", eval(h,
				"local f = io.open('w.txt', 'w') local a, b = f:read('l') return tostring(a) .. ' ' .. b"));
		assertEquals("nil Bad file descriptor", eval(h,
				"local f = io.open('r.txt', 'r') local a, b = f:write('y') return tostring(a) .. ' ' .. b"));
	}

	@Test
	void closedFileCannotBeUsedAndIoTypeKnowsIt() {
		MemHost h = new MemHost();
		h.put("c.txt", "x");
		assertEquals("file closed file nil", eval(h,
				"local f = io.open('c.txt') local a = io.type(f) f:close() return a .. ' ' .. io.type(f) .. ' ' .. tostring(io.type(42))"));
		LuaError e = assertThrows(LuaError.class, () -> eval(h, "local f = io.open('c.txt') f:close() f:read('l')"));
		assertTrue(e.getMessage().contains("attempt to use a closed file"), e.getMessage());
		assertEquals("file (closed)", eval(h, "local f = io.open('c.txt') f:close() return tostring(f)"));
		assertEquals(3, h.closed, "one close per script above");
	}

	@Test
	void standardStreams() {
		MemHost h = new MemHost();
		h.in = "hello\n42\n".getBytes(StandardCharsets.UTF_8);
		eval(h, "local a = io.read('l') local n = io.read('n') io.write('got ', a, ' ', n, '\\n') io.stderr:write('oops')"
				+ " io.stdout:write('!')");
		assertEquals("got hello 42\n!", h.out.toString());
		assertEquals("oops", h.err.toString());
	}

	@Test
	void standardFilesCannotBeClosed() {
		MemHost h = new MemHost();
		assertEquals("nil cannot close standard file nil cannot close standard file", eval(h,
				"local a, b = io.stdout:close() local c, d = io.close() return tostring(a) .. ' ' .. b .. ' ' .. tostring(c) .. ' ' .. d"));
	}

	@Test
	void noScriptAccessToHostFileSystemWithoutTheHost() {
		// The host decides: a file system that refuses everything gives errors, never a real file.
		LuaFileSystem refuse = new LuaFileSystem() {
			public LuaFile open(String path, String mode) throws IOException {
				throw new IOException("Permission denied");
			}

			public LuaFile stdin() {
				return null;
			}

			public LuaFile stdout() {
				return null;
			}

			public LuaFile stderr() {
				return null;
			}
		};
		Globals g = LuaEngine.newEngineGlobals(refuse, new MemHost());
		assertEquals("nil /etc/passwd: Permission denied",
				g.load("local f, m = io.open('/etc/passwd') return tostring(f) .. ' ' .. m", "t").call().tojstring());
	}

	@Test
	void osGetenvClockTime() {
		MemHost h = new MemHost();
		assertEquals("/home/x nil 1.5 1700000000", eval(h,
				"return os.getenv('HOME') .. ' ' .. tostring(os.getenv('NOPE')) .. ' ' .. os.clock() .. ' ' .. os.time()"));
		assertEquals("integer", eval(h, "return math.type(os.time())"));
		LuaError e = assertThrows(LuaError.class, () -> eval(h, "os.time({year = 2000, month = 1, day = 1})"));
		assertTrue(e.getMessage().contains("date table is not supported"), e.getMessage());
	}

	@Test
	void osExitPassesTheStatusToTheHost() {
		MemHost h = new MemHost();
		// A real host ends the process; this one throws, which the VM turns into a Lua error.
		LuaError e = assertThrows(LuaError.class, () -> eval(h, "os.exit(3)"));
		assertTrue(e.getMessage().contains("exit 3"), e.getMessage());
		assertEquals(3, h.exitStatus);
		assertThrows(LuaError.class, () -> eval(h, "os.exit(true)"));
		assertEquals(0, h.exitStatus);
		assertThrows(LuaError.class, () -> eval(h, "os.exit(false)"));
		assertEquals(1, h.exitStatus);
		assertThrows(LuaError.class, () -> eval(h, "os.exit()"));
		assertEquals(0, h.exitStatus);
	}

	@Test
	void osExitRaisesAnErrorIfTheHostDoesNotExit() {
		MemHost h = new MemHost();
		h.exitReturns = true;
		LuaError e = assertThrows(LuaError.class, () -> eval(h, "os.exit(2)"));
		assertTrue(e.getMessage().contains("the host did not exit"), e.getMessage());
		assertEquals(2, h.exitStatus);
	}
}
