# CLAUDE.md

Notes for working on luaj (PlantUML edition). Read `README.md` first: it
explains the goal, the constraints (no reflection, no threads, nothing TeaVM
cannot compile) and what remains to do.

## Layout

- `src/main/java`: the library (`org.luaj.vm2.*`) and the two command-line
  tools `lua.java` / `luac.java` (default package, to be moved or deleted).
- `src/test/java`, `src/test/resources`: JUnit 5 tests and their Lua scripts.
  The scripts are loaded through the class loader.

## Building and testing

Gradle is the reference build (`./gradlew build`), but it needs the network.
Where it is unavailable, use Ant, which works offline:

```
ant test        # compile + run the JUnit 5 tests (build/ is ignored by git)
ant jar         # build/luaj.jar
```

The only jar kept in the repository is the JUnit console launcher in `lib/`.

## Test baseline

A clean run has **213 tests, 201 passing, 12 failing**. Compare failing test
names, not the count. The 12 known failures:

- `CompatibilityTest$JseCompatibilityTest`: `testBaseLib`, `testErrors`,
  `testFunctions`, `testManyUpvals`, `testMathLib`, `testMetatags`,
  `testStringLib`, `testTableLib`, `testTailcalls`, `testUpvalues`, `testVm`
  (reference outputs and base directory are missing).
- `VarargsTest.testNegativeIndices` (message format
  `bad argument #1 (start must be > 0)` vs `bad argument #1: ...`).

## Using clide

clide (semantic navigation, real compiler errors, test runs) works on this
repository as it is: nothing to prepare. It generates its own
`.project`/`.classpath` while its daemon runs and removes them afterwards, so
do not commit any. It only recognises the conventional source folders
(`src/main/java`, `src/test/java`, `src/test/resources`), which is why `core`
and `jse` were merged into `src/main/java`: keep that layout.

```
python3 /path/to/clide/start_clide.py /path/to/luaj
printf 'rebuild\nerrors\n' | python3 /path/to/clide/clide.py /path/to/luaj
```

Expect `0 error(s)` from `rebuild`, and the 12 failures above from
`run_tests failures`. JUnit comes bundled with `clide.jar`; no jar is needed in
`.clide/`.
