# luaj (PlantUML edition)

A small, sandboxed Lua 5.2 interpreter written in pure Java, derived from
[Luaj2](https://github.com/malfact/luaj) (itself a fork of the original
[LuaJ](https://github.com/luaj/luaj)). It is meant to be embedded in
[PlantUML](https://plantuml.com), **not** to be a general-purpose Lua
platform.

The license is unchanged (see `LICENSE`).

## 1. What we are trying to do

PlantUML ships in several editions that must behave identically: the regular
JAR, a JavaScript build and a native C build, the last two produced with
[TeaVM](https://teavm.org). We want Lua inside PlantUML to:

- **drive PlantUML from Lua** (build a model, call layout/render functions,
  query the result);
- **generate diagrams from Lua code embedded in the diagram source**, partly
  replacing the text preprocessor (loops, functions, data structures).

Because the Java edition must have the same Lua as the C edition, Lua is
implemented **in Java**: one interpreter, compiled to JVM bytecode, JavaScript
and C. Hence the constraints that drive every decision in this fork:

- no reflection, no threads, no file system, no `Class.forName`;
- nothing that TeaVM cannot compile;
- no uncatchable failures (a `StackOverflowError` crashes a TeaVM/C binary);
- the smallest possible surface: what is not needed is removed.

### Sketch of the future syntax (not implemented yet)

Lines starting with `$ ` are Lua. `${expr}` interpolates a Lua value into a
PlantUML line. Every line of a function body carries the `$` prefix, like the
`!` repeated in the current preprocessor:

```plantuml
@startuml
$ services = { "Auth", "Billing", "Catalog", "Search" }

$ function color(name)
$   if name == "Auth" then
$     return "#FFCCCC"
$   end
$   return "#CCE5FF"
$ end

actor User

$ for i, s in ipairs(services) do
participant "${s}" as ${s} ${color(s)}
$ end

$ for i, s in ipairs(services) do
User -> ${s} : request ${i - 1}
$ end
@enduml
```

Indentation after `$ ` is free and only matters for readability. The prefix
is heavy for a long block, so a delimited variant is also considered. `$$`
opens and closes a multi-line Lua block, again detected by its prefix (no
`@lua` keyword needed), while `$ ` stays for single lines (loops, conditions
around PlantUML text). The two notations can coexist:

```plantuml
@startuml
$ services = { "Auth", "Billing", "Catalog", "Search" }

$$
function color(name)
  if name == "Auth" then
    return "#FFCCCC"
  end
  return "#CCE5FF"
end
$$

actor User

$ for i, s in ipairs(services) do
participant "${s}" as ${s} ${color(s)}
$ end
@enduml
```

Driving PlantUML from Lua (a `puml.*` API) is not designed yet.

## 2. Where we are

Branch `plantuml1`. The library has been stripped and hardened, but it is
still "LuaJ minus things"; nothing PlantUML-specific exists yet.

Removed:

- coroutines (`LuaThread`, `CoroutineLib`, the thread type);
- `debug` library and the hooks in the interpreter loop;
- `io` and `os` libraries and every file-system access;
- `luajava`/coercion, `luajc` (bytecode generation), the JSR-223 script engine,
  the AST/parser modules, the server, obsolete examples;
- reflection in `LibFunction.bind` (now takes a `Supplier`);
- Java ME leftovers: `JseMathLib` and `JseStringLib` are merged into
  `MathLib` and `StringLib`;
- `String.format` (`%e/%f/%g` are formatted by `StringLib` itself, with exact
  half-even rounding through `BigDecimal`, like C `printf`).

Added or changed:

- **Call depth limit**: `Globals.maxCallDepth` (default 200). Infinite
  recursion raises a catchable `LuaError("stack overflow")`. Tail calls are
  not counted.
- **Sandbox model**: no scripts or modules are found on disk. `require` and
  `loadfile` go through the `Globals.finder` (`ResourceFinder`) installed by
  the host.
- **Java 11** is the language level (shared with the TeaVM builds).
- Tests: 201 of 213 pass; the 12 failures are known and pre-existing (see
  below).

Runtime dependencies: none (JDK only). Test dependency: JUnit 5.

## 3. What remains to do / to clean

Cleaning:

- `CompatibilityTest`: its reference outputs and directory are missing. Revive
  it against a real `lua5.2` and fix its base directory (11 of the 12 known
  failures). `VarargsTest.testNegativeIndices` is the twelfth.
- `lua.java` and `luac.java` (command-line tools) still use `System.*`,
  `Class.forName(...).newInstance()` and files. Move them to tests/tools or
  delete them.
- Merge the `core` and `jse` source directories; `JsePlatform` is the only
  piece that really belongs to "the platform". Rename it.
- `string.dump`, `load` of binary chunks, `LoadState`/`DumpState`: do we keep
  precompiled chunks? They are a sandbox risk and dead weight.
- `dofile`, `loadfile`, `collectgarbage`, `print` (writes to a `PrintStream`):
  decide what a PlantUML script should see.
- Static state: `Print.ps`, `LuaString.s_metatable`, `MathLib` random
  generator. Several `Globals` in one process (or several threads on the
  JVM) must not interfere.
- Rename the packages? (`org.luaj.vm2` is kept for now to ease merging
  upstream fixes.)
- Rewrite the Javadoc, which still describes Java ME, `luajava`, coroutines.
- Keep the Gradle wrapper working: `gradle-wrapper.jar` is not in the
  repository yet.

New work:

1. First TeaVM compilation, JS then C, to see what breaks.
2. `puml.*` API (what Lua can call, what it receives).
3. The `$` / `${}` / `$$` front end: where it plugs into the preprocessor,
   how errors map back to diagram line numbers.
4. Deterministic behavior across editions (iteration order, number
   formatting, `math.random`).
5. Limits besides call depth: instruction count, memory, string size.
6. Performance baseline (startup time matters for the C build).

## 4. Pitfalls

- **Numbers**: Lua 5.2 numeric model. Integers are 32-bit `int` internally
  with automatic promotion to `double`; there are no 64-bit integers.
  `-0.0` is read as integer `0`, so `string.format("%f", -0.0)` prints
  `0.000000`.
- **`StackOverflowError` cannot be caught on TeaVM/C**; only the explicit
  `maxCallDepth` protects. The parser has its own nesting limit (`LexState`).
  The depth is counted per `LuaClosure.execute`; Java frames in between
  (metamethods, `pcall`, `string.gsub` callbacks...) are deep, so the default
  is deliberately small: 200 fits in a default 1 MB Windows stack.
- **`BigDecimal`** is used by `string.format`. It must be supported by the
  TeaVM C classlib; not verified yet.
- **`.gitignore`**: the original ignored `lib/` and `*.lua`, which matched the
  Java package `org.luaj.vm2.lib` and all test scripts. It is fixed now, but
  old clones may still need `git add -f` for such files.
- **Test baselines**: compare failing test names, not the count; a clean run
  currently has exactly the 12 known failures.
- **Upstream**: this fork has diverged heavily; merging upstream changes is
  not realistic.
- A `$` line is Lua only when the prefix is at the start of the line. Any
  character-level ambiguity with `${...}` and with `$$` inside PlantUML
  text must be settled before the syntax is frozen.

## 5. Open questions

- Is Lua 5.2 enough, or do we want 5.3 integers (64-bit)? It costs size and
  speed on JS and C.
- Which parts of the PlantUML preprocessor does Lua replace, and which stay
  (`!include`, `!theme`, variables)? Do both coexist in one diagram, and in
  which order are they evaluated?
- `$` single-line only, `$$` blocks only, or both?
- How are `${expr}` values escaped when they contain PlantUML syntax?
- Do Lua scripts share one global state across `!include`d files?
- What does `require` resolve against (stdlib resources, URLs, the `!include`
  search path)? Is it allowed at all in the online/server editions?
- Resource limits and timeouts for untrusted diagrams (server editions).
- Should the Lua API be able to read the diagram result (positions,
  sizes), or only produce text?
- Keep the `org.luaj.vm2` package name and the Luaj license header, or
  rename?

## Building

```
./gradlew build
```

Requires JDK 11 or later (the build uses `--release 11`).
