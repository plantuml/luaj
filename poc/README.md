# Python interpreter proof of concept: JVM / JavaScript / C

Compiles the interpreter (the sources of the parent directory, nothing is
copied) with TeaVM to JavaScript and to C, and runs one test program
(`org.luaj.poc.Main`) on the three editions to compare their outputs.

The program prints: 44 floats through `repr`, `%f %e %g` and format specs (the
`BigDecimal` part, to check on the C edition; the JVM output equals CPython's),
then small Python programs (closures, strings with non-BMP characters,
containers, integers, exceptions, 32-bit overflow, an uncaught error), then the
limits (catchable `RecursionError`, uncatchable instruction limit, syntax error,
deeply nested `repr`), then `== done ==` and timings.

This build is independent of the one in the parent directory. Run it with the
wrapper of the parent (from this `poc` directory):

```
../gradlew jvmReference     # JVM run, writes build/jvm.txt
../gradlew generateJavaScript
../gradlew generateC
```

The TeaVM version is `0.16.0` (the one of the PlantUML build); Gradle must run
on JDK 17 or later; the bytecode stays Java 11. Lines starting with `#` are
timings (not comparable); `expected.txt` is the reference output without them.

JVM: `../gradlew jvmReference; diff expected.txt build/jvm.txt`.
JavaScript: `node -e "require('./build/generated/teavm/js/luaj-poc.js').main([])" | grep -v '^#' | diff expected.txt -`.
C: `generateC`, then `cmake -S build/generated/teavm/c -B build/c-build`,
`cmake --build build/c-build --config Release`, run `bin/poc-c`, filter the `#`
lines and compare.

Things to report: any difference in the `floats` lines, any crash on the
`limits` programs (deep recursion on the native stack), the timings.

## Findings

First run of the C edition (TeaVM 0.16.0): everything matched the JVM except
three `NumberFormatter` lines, all caused by TeaVM's `BigDecimal`
(`round(MathContext)`/`precision()` wrong for 1.5 with one digit, 1e16 with 16
digits, 1e23 with 16 digits). `NumberFormatter` now rounds the digits itself and
only uses `new BigDecimal(double).toPlainString()` (exact expansion, which was
correct); `NumberFormatterTest` checks it against the JVM's `BigDecimal` on
20000 random values.

Second finding (TeaVM C 0.16.0): the previous (Lua) interpreter was 30 to 60 times
slower than on the JVM (`fib(27)`: 5.6 s). The cause was not the heap size
(though a fixed heap, `minHeapSize = maxHeapSize`, is needed: the 1 MB default
heap crashed with a bus error / segmentation fault while growing) and not
allocation, but the **entry into a `try` block**: about 200 ns each, against a
few ns for the calls made inside it. The interpreter loop was entirely inside
one `try`, and the cost came from the many method calls of each instruction.
With the loop moved out of the `try` (its `run`), `fib(27)` takes 66 ms
on Windows (MSVC, ARM64) and a loop iteration 37 ns. What remains is about
110 ns per Lua function call (the `try` of its `execute`, entered once
per call) and 190 ns per `pcall`.


## Not yet checked for the Python interpreter

The editions have not been built yet since the Lua interpreter was replaced.
The first thing to look at is the cost of entering a `try` block on TeaVM C
described above: `Interp.run` has its whole loop inside one.
