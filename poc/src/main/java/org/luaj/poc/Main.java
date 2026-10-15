package org.luaj.poc;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.NumberFormatter;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Test program for the editions of luaj: JVM, JavaScript (TeaVM) and C (TeaVM).
 * <p>
 * Everything it prints is deterministic and must be identical on the three
 * editions, except the lines that start with '#' (timings).
 */
public class Main {

	private static int errors = 0;

	public static void main(String[] args) {
		System.out.println("== luaj poc ==");
		numberFormatter();
		luaScripts();
		System.out.println("== done, " + errors + " error(s) ==");
		bench();
	}

	// ------------------------------------------------------------------
	// 1. NumberFormatter (BigDecimal): the part that must be checked on TeaVM C
	// ------------------------------------------------------------------

	private static final double[] NUMBERS = { 0.0, -0.0, 1.0, -1.0, 0.1, 0.2, 0.1 + 0.2, 1.0 / 3, 2.0 / 3, 0.5, 1.5, 2.5, 3.5,
			0.125, 0.375, 100.0, 123456.789, 1e14, 1e15, 1e16, 1e21, 1e100, 1e300, 1.7976931348623157e308,
			4.9e-324, 2.2250738585072014e-308, 1e-5, 1e-4, 1e-10, 9.995, 99.5, 999999.5, 0.000123456,
			3.141592653589793, 2.718281828459045, 255.0 / 256, 4503599627370496.0, 9007199254740993.0,
			9.223372036854775807e18, 0.3, 0.7, 1e23, 5e-324, 123456789012345678.0 };

	private static final String[] FORMATS = { "f0", "f2", "f10", "e0", "e3", "e15", "g0", "g1", "g6", "g14", "g17" };

	private static void numberFormatter() {
		System.out.println("-- NumberFormatter");
		for (int i = 0; i < NUMBERS.length; i++) {
			double v = NUMBERS[i];
			StringBuilder sb = new StringBuilder();
			sb.append(i).append(" tostring=").append(NumberFormatter.tostring(v));
			for (String f : FORMATS) {
				int prec = Integer.parseInt(f.substring(1));
				sb.append(' ').append(f).append('=');
				if (v != v || v == 0 && 1 / v < 0) {
					sb.append('-'); // the sign of -0.0 is handled by string.format, not by NumberFormatter
					continue;
				}
				switch (f.charAt(0)) {
				case 'f':
					// huge values make long strings, but they are exact and must match
					sb.append(NumberFormatter.fixed(Math.abs(v), prec, false));
					break;
				case 'e':
					sb.append(NumberFormatter.exponent(Math.abs(v), prec, false));
					break;
				default:
					sb.append(NumberFormatter.general(Math.abs(v), prec, false));
				}
			}
			System.out.println(sb);
		}
		// the same numbers through the Lua string library
		Globals g = JsePlatform.standardGlobals();
		StringBuilder sb = new StringBuilder();
		for (double v : NUMBERS)
			sb.append(g.load("local v = ... return string.format('%.3f|%10.2e|%-12g|%g', v, v, v, v)", "fmt").call(LuaValue.valueOf(v)).tojstring()).append('\n');
		System.out.print(sb);
	}

	// ------------------------------------------------------------------
	// 2. Lua scripts
	// ------------------------------------------------------------------

	private static String lines(String... l) {
		StringBuilder sb = new StringBuilder();
		for (String s : l)
			sb.append(s).append('\n');
		return sb.toString();
	}

	private static void luaScripts() {
		run("integers", lines(
				"local t = {}",
				"local function p(...) local a = table.pack(...) for i = 1, a.n do a[i] = tostring(a[i]) end t[#t+1] = table.concat(a, ' ') end",
				"p(math.maxinteger, math.mininteger, math.maxinteger + 1, math.mininteger - 1)",
				"p(7 // 2, -7 // 2, 7 % -3, -7 % 3, 7.5 // 2, 2^10, 10 / 2, 3 | 4, 6 & 3, 5 ~ 1, ~0, 1 << 31, 1 << 32, -1 >> 28)",
				"p(math.type(1), math.type(1.0), math.type('1'), math.tointeger(3.0), math.tointeger(3.5))",
				"p(0xffffffff, 0x7fffffff, 2147483648, -2147483648, 1e3, 0x10, 1 == 1.0, '10' + 5, '0x10' + 0)",
				"p(string.format('%d %5d %-5d| %x %X %o %c %q', 42, 42, 42, 255, 255, 8, 65, 'hi\\n'))",
				"p(tostring(1/0), tostring(-1/0), tostring(0/0), tostring(-0.0), 3 .. '', 3.0 .. '')",
				"p(pcall(function() return 1 // 0 end))",
				"p(pcall(function() return 1 % 0 end))",
				"p(pcall(function() return 1.5 | 0 end))",
				"return table.concat(t, '\\n')"));

		run("utf8", lines(
				"local t = {}",
				"local function p(...) local a = table.pack(...) for i = 1, a.n do a[i] = tostring(a[i]) end t[#t+1] = table.concat(a, ' ') end",
				"local s = 'h\\u{e9}llo \\u{20ac} \\u{1F600}'",
				"p(#s, utf8.len(s), utf8.codepoint(s, 1, -1))",
				"for pos, c in utf8.codes(s) do p(pos, c) end",
				"p(utf8.char(72, 228, 8364, 128512) == 'H\\u{e4}\\u{20ac}\\u{1F600}')",
				"p(utf8.offset(s, 3), utf8.offset(s, -1), utf8.len('\\xff'))",
				"local n = 0 for ch in s:gmatch(utf8.charpattern) do n = n + 1 end p(n)",
				"return table.concat(t, '\\n')"));

		run("strings", lines(
				"local t = {}",
				"local function p(...) local a = table.pack(...) for i = 1, a.n do a[i] = tostring(a[i]) end t[#t+1] = table.concat(a, ' ') end",
				"p(('hello world'):upper(), ('abc'):rep(3, '-'), ('hello'):sub(2, -2), ('hello'):find('l+'))",
				"p(('key = value'):match('^(%w+)%s*=%s*(%w+)$'))",
				"p(('a,b,,c'):gsub(',', ';'))",
				"p(string.format('%5.1f|%-8s|%s|%i', 3.14159, 'ab', nil, 7))",
				"local words = {} for w in ('the quick brown fox'):gmatch('%a+') do words[#words+1] = w end p(#words, words[4])",
				"p(('x'):byte(), string.char(72, 105), ('abc'):reverse(), #('abc'):rep(1000))",
				"p(tostring(12), tostring(12.5), tostring(true), tostring(nil), tonumber('  0x1p4  '), tonumber('z', 36), tonumber('1e2'))",
				"return table.concat(t, '\\n')"));

		run("tables", lines(
				"local t = {}",
				"local function p(...) local a = table.pack(...) for i = 1, a.n do a[i] = tostring(a[i]) end t[#t+1] = table.concat(a, ' ') end",
				"local a = {5, 2, 8, 1, 9, 3}",
				"table.sort(a) p(table.concat(a, ','))",
				"table.sort(a, function(x, y) return x > y end) p(table.concat(a, ','))",
				"table.insert(a, 100) table.insert(a, 1, 0) p(table.concat(a, ','), table.remove(a), table.remove(a, 1), #a)",
				"p(table.concat(table.move({1, 2, 3}, 1, 3, 2), ','), select('#', table.unpack({1, nil, 3}, 1, 3)))",
				"local seen = {} for i, v in ipairs({10, 20, 30}) do seen[#seen+1] = i * v end p(table.concat(seen, ','))",
				"local keys = {} for k in pairs({a = 1, b = 2, c = 3}) do keys[#keys+1] = k end table.sort(keys) p(table.concat(keys))",
				"local mt = setmetatable({}, {__index = function(_, k) return k * 2 end}) p(mt[21], rawget(mt, 21))",
				"local v = setmetatable({}, {__add = function(a, b) return 'added' end, __tostring = function() return 'V' end, __len = function() return 42 end})",
				"p(v + 1, tostring(v), #v)",
				"return table.concat(t, '\\n')"));

		run("functions", lines(
				"local t = {}",
				"local function p(...) local a = table.pack(...) for i = 1, a.n do a[i] = tostring(a[i]) end t[#t+1] = table.concat(a, ' ') end",
				"local function fib(n) if n < 2 then return n end return fib(n - 1) + fib(n - 2) end",
				"p(fib(20))",
				"local function counter() local c = 0 return function() c = c + 1 return c end end",
				"local c1, c2 = counter(), counter() c1() c1() p(c1(), c2())",
				"local function va(...) return select('#', ...), (select(2, ...)) end p(va('a', 'b', 'c'))",
				"p(select(-1, 1, 2, 3))",
				"local ok, err = pcall(error, {code = 7}) p(tostring(ok), type(err), err.code)",
				"local ok2, err2 = pcall(function() local x = nil; return x.field end) p(tostring(ok2), (err2:gsub('^.-:%d+: ', '')))",
				"p(select(2, xpcall(function() error('boom') end, function(m) return 'handled ' .. m end)))",
				"local function tail(n) if n == 0 then return 'done' end return tail(n - 1) end p(tail(100000))",
				"p(pcall(function() local function r() return 1 + r() end return r() end))",
				"for i = 3, 1, -1 do t[#t+1] = 'loop ' .. i end",
				"for i = 1, 3 do goto continue; ::continue:: end",
				"local s = 0 for i = 1, 100 do s = s + i end p(s)",
				"p(load('return 1 + 2')(), pcall(load, 'this is not lua'))",
				"p(select('#', load('syntax error here')))",
				"return table.concat(t, '\\n')"));
	}

	private static void run(String name, String script) {
		System.out.println("-- lua " + name);
		try {
			Globals g = JsePlatform.standardGlobals();
			System.out.println(g.load(script, name).call().tojstring());
		} catch (Throwable e) {
			errors++;
			System.out.println("ERROR in " + name + ": " + e);
		}
	}

	// ------------------------------------------------------------------
	// 3. Timings ('#' lines: not compared)
	// ------------------------------------------------------------------

	private static void bench() {
		try {
			Globals g = JsePlatform.standardGlobals();
			LuaValue fib = g.load("local function fib(n) if n < 2 then return n end return fib(n-1) + fib(n-2) end return fib(27)", "b1");
			long t0 = System.nanoTime();
			LuaValue r = fib.call();
			long t1 = System.nanoTime();
			System.out.println("# fib(27) = " + r.tojstring() + " in " + (t1 - t0) / 1000000 + " ms");
			LuaValue fmt = g.load("local n = 0 for i = 1, 20000 do n = n + #string.format('%.3f %g', i / 7, i * 1.1) end return n", "b2");
			t0 = System.nanoTime();
			r = fmt.call();
			t1 = System.nanoTime();
			System.out.println("# 20000 x string.format = " + r.tojstring() + " in " + (t1 - t0) / 1000000 + " ms");
		} catch (Throwable e) {
			System.out.println("# bench failed: " + e);
		}
	}
}
