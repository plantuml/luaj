# luaj proof of concept: JVM / JavaScript / C

Compiles luaj (the sources of the parent directory, nothing is copied) with
TeaVM to JavaScript and to C, and runs one test program (`org.luaj.poc.Main`)
on the three editions to compare their outputs.

The main goal is to check `BigDecimal`, used by `NumberFormatter` for every
float-to-string conversion (`tostring`, `print`, `string.format("%f %e %g")`),
on the C edition: correctness (half-even rounding, huge and tiny exponents,
subnormals) and speed. The program also runs Lua scripts that exercise the
whole interpreter (integers that wrap, bitwise operators, `utf8`, strings,
tables, closures, `pcall`, tail calls, stack overflow, `load`).

This build is independent of the one in the parent directory. Run it with the
wrapper of the parent (from this `poc` directory):

```
../gradlew jvmReference     # JVM run, writes build/jvm.txt
../gradlew generateJavaScript
../gradlew generateC
```

The TeaVM version is `0.16.0` (the one of the PlantUML build); change it in the
`plugins` block of `build.gradle.kts`. Gradle must run on JDK 17 or later
(TeaVM requirement); the bytecode stays Java 11.
The task names and the output folders (under `build/generated/teavm/`) are
those of the TeaVM Gradle plugin; if your version names them differently,
`../gradlew tasks --all | grep -i generate` lists them.

## What the program prints

- `-- NumberFormatter`: 44 doubles, each one with `tostring` and with `%f`,
  `%e`, `%g` at several precisions, then the same doubles through
  `string.format`.
- `-- lua <name>`: the result of each Lua script.
- `== done, N error(s) ==`.
- Lines starting with `#`: timings (not comparable, ignored below). They are
  micro-benchmarks that isolate one cost each: Java level (allocations, boxed
  numbers, `try/finally`, exceptions, `Varargs`), then Lua level (loops, table
  access, calls of Lua and Java functions, closures, `pcall`, `fib` of growing
  size to see whether the time per call grows). Compare the three editions
  line by line to find what makes the calls slow on TeaVM C.

`expected.txt` is the reference output (JVM, timings removed). The
`NumberFormatter` part has also been checked against C `printf` (glibc) for all
the `%f/%e/%g` values: no difference.

## Comparing

JVM, to check that the reference is up to date:

```
../gradlew jvmReference
diff expected.txt build/jvm.txt
```

JavaScript (Node.js; `generateJavaScript` writes `luaj-poc.js`, which prints
with `console.log`):

```
node -e "require('./build/generated/teavm/js/luaj-poc.js').main([])" > build/js-raw.txt
grep -v '^#' build/js-raw.txt | diff expected.txt -
```

C: `generateC` writes the C sources in `build/generated/teavm/c` and a
Gradle task then copies `CMakeLists.txt` (from this directory) next to them.
Build with CMake (Windows: MSVC or clang-cl, add `-A ARM64` or `-A x64`), the
executable is `bin/poc-c` under that directory:

```
../gradlew generateC
cmake -S build/generated/teavm/c -B build/c-build
cmake --build build/c-build --config Release
build/generated/teavm/c/bin/poc-c > build/c-raw.txt
grep -v '^#' build/c-raw.txt | diff expected.txt -
```

On Windows: `findstr /v /b "#" build\c-raw.txt > build\c.txt` then
`fc expected.txt build\c.txt`.

Things to report: any difference in the `NumberFormatter` lines (digits,
rounding), any `ERROR in ...` line, a crash on the `stack overflow` script
(deep recursion on the native stack), and the `#` timings for the three
editions (`fib(27)` and 20000 `string.format`).

## Findings

First run of the C edition (TeaVM 0.16.0): everything matched the JVM except
three `NumberFormatter` lines, all caused by TeaVM's `BigDecimal`
(`round(MathContext)`/`precision()` wrong for 1.5 with one digit, 1e16 with 16
digits, 1e23 with 16 digits). `NumberFormatter` now rounds the digits itself and
only uses `new BigDecimal(double).toPlainString()` (exact expansion, which was
correct); `NumberFormatterTest` checks it against the JVM's `BigDecimal` on
20000 random values.

Second finding (TeaVM C 0.16.0): the Lua interpreter was 30 to 60 times
slower than on the JVM (`fib(27)`: 5.6 s). The cause was not the heap size
(though a fixed heap, `minHeapSize = maxHeapSize`, is needed: the 1 MB default
heap crashed with a bus error / segmentation fault while growing) and not
allocation, but the **entry into a `try` block**: about 200 ns each, against a
few ns for the calls made inside it. The interpreter loop was entirely inside
one `try`, and the cost came from the many method calls of each instruction.
With the loop moved out of the `try` (`LuaClosure.run`), `fib(27)` takes 0.6 s
and a loop iteration 20 ns. What remains is about 1 µs per Lua function call
(the `try` of `LuaClosure.execute`, entered once per call) and 1.5 µs per `pcall`.

## Known quirk (not related to TeaVM)

`xpcall` does not call its message handler for `error("msg", 0)`; it does for
errors raised with a position. The test program avoids that case.
