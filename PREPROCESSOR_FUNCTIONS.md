# Preprocessor functions: what happens to them

Design notes (draft, nothing here is implemented). Companion to `README.md`.

The current PlantUML preprocessor ships a standard library of about 75
built-in functions, all named `%something(...)`. If Lua replaces most of the
preprocessor logic (see `README.md`, sections 1 and 6), each of them needs a
decision: **drop** (Lua does it natively), **provide** (a `puml.*` function), or
**remove** (conflicts with the sandbox or with determinism).

The list below comes from the built-in function classes of the PlantUML
source code (one class per function, registered in `StandardFunctions`).

## Principles

- Legacy diagrams keep working unchanged: the old preprocessor and its `%`
  functions stay, frozen and deprecated.
- A diagram that contains a Lua line (`$ ` or `$$`) is a Lua diagram: the
  legacy logic directives and `%` functions are then an explicit error, so the
  two worlds never interact. (Proposal, see the open questions in `README.md`.)
- Whatever Lua does natively is not re-exported under another name.
- Anything that needs PlantUML internals (color parsing, themes, stdlib
  catalog) goes in `puml.*`.
- Anything that reads the machine (clock, environment, file system) is
  removed, or injected by the host through `puml.args`, to keep the sandbox
  and the identical output on every edition.

## Behaviors to keep in mind when porting

These differences matter more than the function names:

- **Indexing**: `%substr` and `%strpos` are 0-based and `%strpos` returns `-1`
  when not found. Lua is 1-based and returns `nil`.
- **Length**: `%strlen` is Java `String.length()` (UTF-16 units). Lua `#s`
  counts bytes. For code points, use `utf8.len`.
- **`%splitstr`** uses a Java `StringTokenizer`: the separator is a *set of
  characters* and empty tokens are dropped. There is no direct Lua equivalent
  (`gmatch` keeps different semantics); a helper may be worth providing.
- **`%splitstr_regex`** uses Java regular expressions. Lua patterns are
  different and not equivalent.
- **`%random(min, max)`** excludes `max`. The Lua `math.random(m, n)` includes
  both. Also see determinism below.
- **`%mod`** raises an error on a zero divisor. Check what the Lua `%` operator
  does in the target number model (error or NaN) before relying on it.
- **`%intval` / `%boolval`** raise an error on bad input, whereas `%hex2dec`
  silently returns `0` and `%dec2hex` returns an empty string.
- **`%dec2hex`** gives lowercase digits, as does `string.format("%x")`.
- Boolean values are returned as `1` / `0` or `true` / `false` depending on the
  function; Lua has real booleans.

## Table

Legend: **native** = plain Lua, **puml** = a function in `puml.*`,
**host** = injected through `puml.args` or provided by the host, **remove** = no
replacement, **keep legacy** = only available in non-Lua diagrams.

### Strings and numbers

| Legacy function | Lua replacement | Status |
| --- | --- | --- |
| `%strlen(s)` | `#s`, or `utf8.len(s)` | native |
| `%substr(s, pos [, len])` | `s:sub(pos + 1, pos + len)` | native (watch the base) |
| `%strpos(s, t)` | `s:find(t, 1, true)` | native (watch the base) |
| `%upper(s)`, `%lower(s)` | `s:upper()`, `s:lower()` | native |
| `%string(v)` | `tostring(v)` | native |
| `%intval(s)` | `math.tointeger(tonumber(s))` | native |
| `%boolval(v)` | a one-line helper, or plain Lua conditions | native |
| `%mod(a, b)` | `a % b` | native |
| `%dec2hex(n)`, `%hex2dec(s)` | `string.format("%x", n)`, `tonumber(s, 16)` | native |
| `%chr(n)`, `%ord(s)` | `utf8.char(n)`, `utf8.codepoint(s)` | native (needs `utf8`) |
| `%splitstr(s, sep)` | helper, see above | puml (`puml.split`) to be decided |
| `%splitstr_regex(s, re)` | none | remove (Lua patterns instead) |
| `%size(v)` | `#t` for a table or string | native |
| `%eval(expr)` | `load("return " .. expr)()` | native, but `load` of text needs a decision in the sandbox |

### Logic

| Legacy function | Lua replacement | Status |
| --- | --- | --- |
| `%true()`, `%false()` | `true`, `false` | native |
| `%not`, `%and`, `%or`, `%xor`, `%nand`, `%nor`, `%nxor` | `not`, `and`, `or`, `~=` and combinations | native |

### Text and layout constants

| Legacy function | Lua replacement | Status |
| --- | --- | --- |
| `%newline()`, `%breakline()`, `%n()` | `"\n"` for source text. In *rendered* text PlantUML uses its own newline marker, so a constant is needed | puml (`puml.NL`) to be decided |
| `%tab()`, `%left_align()`, `%right_align()`, `%backslash()` | internal marker characters used by the renderer | puml (constants) |
| `%percent()`, `%dollar()` | `"%"`, `"$"` | native |
| `%xargs()` | the remaining command line arguments | host (`puml.args`) |

### Variables and functions

| Legacy function | Lua replacement | Status |
| --- | --- | --- |
| `%variable_exists`, `%get_variable_value`, `%set_variable_value` | `_G[name]`, `rawget`, `_G[name] = v` | native |
| `%function_exists`, `%call_user_func` | `type(_G[name]) == "function"`, `_G[name](...)` | native |
| `%invoke_procedure`, `%retrieve_procedure` | call the Lua function | native |

### Colors (needs PlantUML internals)

These go through PlantUML's color parser (named colors, `#RRGGBB`, themes) and
throw a "No such color" error. This is the main block to provide.

| Legacy function | Replacement | Status |
| --- | --- | --- |
| `%lighten(c, ratio)`, `%darken(c, ratio)` | `puml.color.lighten`, `puml.color.darken` | puml |
| `%is_dark(c)`, `%is_light(c)` | `puml.color.is_dark`, `puml.color.is_light` | puml |
| `%reverse_color(c)`, `%reverse_hsluv_color(c)` | `puml.color.reverse`, `puml.color.reverse_hsluv` | puml |
| `%hsl_color(h, s, l [, a])` | `puml.color.hsl` | puml |

### JSON

Lua tables replace JSON values, so most of these disappear.

| Legacy function | Replacement | Status |
| --- | --- | --- |
| `%load_json(path [, default])` | `puml.json.decode(puml.read(path))` | puml (reads through `Globals.finder`, no direct file or URL access) |
| `%str2json(s)` | `puml.json.decode(s)` | puml |
| `%get_json_keys`, `%get_json_type`, `%json_key_exists` | `pairs(t)`, `type(t)`, `t[k] ~= nil` | native |
| `%json_add`, `%json_set`, `%json_remove`, `%json_merge` | plain table assignment, `table.insert`, `table.remove`, a loop | native |

### PlantUML environment

| Legacy function | Replacement | Status |
| --- | --- | --- |
| `%version()` | `puml.version` | puml |
| `%feature(name)` | `puml.feature(name)` | puml |
| `%filename()`, `%filename_no_extension()`, `%dirpath()`, `%filedate()` | `puml.file.*` or injected values | host, to be decided |
| `%get_current_theme()` | `puml.theme` (a table) | puml |
| `%get_all_theme()`, `%get_all_stdlib()`, `%get_stdlib(name)` | `puml.themes()`, `puml.stdlib()` | puml, low priority |

### Conflict with the sandbox or with determinism

| Legacy function | Problem | Proposal |
| --- | --- | --- |
| `%now()`, `%date(...)` | clock, output differs from run to run | remove, or the host passes a date through `puml.args` |
| `%random(...)` | not reproducible across editions | remove, or `math.random` seeded per diagram (already in `README.md`) |
| `%getenv(name)` | leaks the environment, dangerous on a server | remove, or the host injects chosen values through `puml.args` |
| `%file_exists(path)` | direct file system access | remove, or `puml.exists(name)` through `Globals.finder` |

## Not functions, but worth deciding together

- `%min`, `%max`, `%trim`, `%str_replace`, `%time` and the `!exit` / `!log`
  directives appear only as ideas in a comment of the legacy code; they are not
  implemented there. Lua has `math.min`, `math.max`, `string.gsub`, and
  `puml.error` / `print`.
- `!include`, `!theme`, `!pragma` and `!startsub` are not functions and stay in
  the reduced preprocessor (see `README.md`, section 6).

## Suggested order of work

1. `puml.color.*`: the only block that cannot be rewritten in Lua without
   duplicating PlantUML code.
2. `puml.json.*` and `puml.read`.
3. `puml.version`, `puml.feature`, `puml.args`.
4. A small `puml.split` and the newline constants, once the renderer markers
   are settled.
5. A compatibility test: for each legacy function with a native replacement,
   run the same input through both and compare (the base and error cases above
   are the traps).
