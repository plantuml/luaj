package org.luaj.poc;

import org.luaj.vm2.python.ExecutionLimitError;
import org.luaj.vm2.python.Interp;
import org.luaj.vm2.python.PyError;
import org.luaj.vm2.python.PySyntaxError;

/**
 * Test program for the editions of the Python interpreter: JVM, JavaScript
 * (TeaVM) and C (TeaVM).
 * <p>
 * Everything it prints is deterministic and must be identical on the three
 * editions, except the lines that start with '#' (timings).
 */
public class Main {

	private static int errors = 0;

	public static void main(String[] args) {
		System.out.println("== python poc ==");
		numbers();
		scripts();
		limits();
		System.out.println("== done, " + errors + " error(s) ==");
		bench();
	}

	/** Runs a program, returns what it printed (and the error, if any). */
	private static String run(String source, long maxSteps, int maxDepth) {
		final StringBuilder out = new StringBuilder();
		Interp in = new Interp(new Interp.Output() {
			@Override
			public void write(String text) {
				out.append(text);
			}
		});
		in.setMaxSteps(maxSteps);
		in.setMaxDepth(maxDepth);
		try {
			in.run(source, "poc.py");
		} catch (PyError e) {
			out.append("PyError ").append(e.typeName()).append(": ").append(e.text()).append('\n');
		} catch (PySyntaxError e) {
			out.append("PySyntaxError ").append(e.getMessage()).append('\n');
		} catch (ExecutionLimitError e) {
			out.append("ExecutionLimitError\n");
		}
		return out.toString();
	}

	private static void show(String title, String source) {
		System.out.println("-- " + title);
		System.out.print(run(source, 200000000L, 1000));
	}

	// ------------------------------------------------------------------
	// 1. Floats: BigDecimal is the part that must be checked on TeaVM C
	// ------------------------------------------------------------------

	private static final String NUMBERS = "[0.0, -0.0, 1.0, -1.0, 0.1, 0.2, 0.1 + 0.2, 1.0 / 3, 2.0 / 3, 0.5, 1.5, 2.5, 3.5,"
			+ " 0.125, 0.375, 100.0, 123456.789, 1e14, 1e15, 1e16, 1e21, 1e100, 1e300, 1.7976931348623157e308,"
			+ " 4.9e-324, 2.2250738585072014e-308, 1e-5, 1e-4, 1e-10, 9.995, 99.5, 999999.5, 0.000123456,"
			+ " 3.141592653589793, 2.718281828459045, 255.0 / 256, 4503599627370496.0, 9007199254740993.0,"
			+ " 9.223372036854775807e18, 0.3, 0.7, 1e23, 5e-324, 123456789012345678.0]";

	private static void numbers() {
		show("floats", "for i, v in enumerate(" + NUMBERS + "):\n"
				+ "    print(i, repr(v), '%.0f' % v, '%.2f' % v, '%.10f' % v, '%.0e' % v, '%.3e' % v,"
				+ " '%.15e' % v, '%g' % v, '%.14g' % v, '%.17g' % v, f'{v:10.3f}', f'{v:,.1f}')\n");
	}

	// ------------------------------------------------------------------
	// 2. Scripts that exercise the whole interpreter
	// ------------------------------------------------------------------

	private static void scripts() {
		show("fib", "def fib(n):\n    return n if n < 2 else fib(n - 1) + fib(n - 2)\nprint(fib(20))\n");
		show("closures", "def counter():\n    n = 0\n    def inc():\n        nonlocal n\n        n += 1\n        return n\n"
				+ "    return inc\nc = counter()\nc(); c()\nprint(c(), [lambda x, i=i: x * i for i in range(3)][2](5))\n");
		show("strings", "s = 'h\u00e9llo w\u00f6rld \ud83d\ude00'\nprint(len(s), f'{s.upper()!a}', f'{s[::-1]!a}', s.split() == ['h\u00e9llo', 'w\u00f6rld', '\ud83d\ude00'],"
				+ " f'{s!a}', 'a,b,,c'.split(','), '-'.join(['x', 'y']), 'abc'.center(9, '*'))\n");
		show("containers", "d = {'a': 1, 'b': [1, 2, (3, 4)]}\nd['c'] = d['b'][2][1]\nprint(d, sorted(d), list(d.items()))\n"
				+ "print([x * x for x in range(10) if x % 2], {k: v for k, v in zip('abc', range(3))})\n"
				+ "print(sorted([3, 1, 2], reverse=True), min(4, 2, 8), max([1, 5, 3]), sum(range(100)))\n");
		show("integers", "print(7 // 2, -7 // 2, 7 % -3, 2 ** 10, 1 << 20, ~5, -2147483648, 7 / 2, divmod(-7, 2))\n"
				+ "print(int('ff', 16), hex(255), oct(8), bin(5), round(2.5), round(3.5), round(2.675, 2))\n");
		show("exceptions", "def f(x):\n    try:\n        return 10 // x\n    except ZeroDivisionError as e:\n"
				+ "        return 'zero: ' + str(e)\n    else:\n        pass\nprint(f(2), f(0))\n"
				+ "try:\n    [][1]\nexcept (KeyError, IndexError) as e:\n    print(type(e) is IndexError, e.args)\n"
				+ "try:\n    raise ValueError('bad')\nexcept Exception as e:\n    print(repr(e))\n");
		show("overflow", "try:\n    print(2147483647 + 1)\nexcept OverflowError:\n    print('overflow')\n");
		show("uncaught", "x = 1\nprint(x)\nraise KeyError('k')\n");
	}

	private static void limits() {
		System.out.println("-- limits");
		System.out.print(run("def f():\n    f()\ntry:\n    f()\nexcept RecursionError:\n    print('recursion caught')\n",
				200000000L, 200));
		System.out.print(run("try:\n    while True:\n        pass\nexcept:\n    print('must not be reached')\n", 100000L, 1000));
		System.out.print(run("def (:\n", 1000L, 1000));
		System.out.print(run("a = []\nfor i in range(100000):\n    a = [a]\ntry:\n    repr(a)\nexcept RecursionError:\n"
				+ "    print('deep repr caught')\n", 200000000L, 1000));
	}

	// ------------------------------------------------------------------
	// 3. Timings (lines starting with '#', ignored by the comparison)
	// ------------------------------------------------------------------

	private static void bench() {
		String fib = "def fib(n):\n    return n if n < 2 else fib(n - 1) + fib(n - 2)\nprint(fib(25))\n";
		long t0 = System.currentTimeMillis();
		String r = run(fib, 2000000000L, 1000);
		long t1 = System.currentTimeMillis();
		System.out.println("# fib(25) = " + r.trim() + " in " + (t1 - t0) + " ms");
		String loop = "t = 0\nfor i in range(2000000):\n    t += i % 7\nprint(t)\n";
		t0 = System.currentTimeMillis();
		r = run(loop, 2000000000L, 1000);
		t1 = System.currentTimeMillis();
		System.out.println("# loop = " + r.trim() + " in " + (t1 - t0) + " ms");
		String fmt = "s = 0\nfor i in range(20000):\n    s += len('%.3f %e' % (i / 7, i * 1.1))\nprint(s)\n";
		t0 = System.currentTimeMillis();
		r = run(fmt, 2000000000L, 1000);
		t1 = System.currentTimeMillis();
		System.out.println("# 20000 float formats = " + r.trim() + " in " + (t1 - t0) + " ms");
	}
}
