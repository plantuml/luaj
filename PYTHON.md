# Python subset: design notes

Working document for the `python1` branch. The Lua front-end and the Lua
semantics are being dropped: the goal is an interpreter for a **subset of
Python 3**, in pure Java (same constraints as before: no reflection, no
threads, nothing TeaVM cannot compile), embedded in PlantUML. Everything here
is a design, nothing is implemented yet.

## 1. Principles

- **A subset, not a dialect.** Every accepted program is valid Python 3 and
  gives the same result in CPython, except for the documented deviations
  (section 4). What is hard or useless here is removed (classes, generators,
  `finally`, ...), never changed.
- **CPython is the oracle.** The test scripts in `src/test/resources/python`
  run under CPython first; their output is the reference. See section 7.
- **Deterministic and bounded.** Same output on the JAR, JavaScript and C
  editions. An instruction counter and a call depth limit bound every run.
- **The VM may change.** The bytecode is ours to modify. It starts from the
  Lua 5.3 instruction format (iABC / iABx / iAsBx, 6-bit opcode) because the
  machinery exists, and evolves toward Python.

## 2. The language subset

Statements: expression, assignment (chained, tuple unpacking, augmented,
targets `name`, `a[i]`, `a[i:j]`), `if/elif/else`, `while`, `for ... in`
(with `break`, `continue`, and `else` on loops), `pass`, `def` (positional
and keyword parameters, defaults, `*args`, `**kwargs`), `return`, `global`,
`nonlocal`, `assert`, `del a[i]`, `del name`, `try/except/else`, `raise`,
`import math` (builtin modules only).

Expressions: `None True False`, int / float / str literals (single, double
and triple quotes, escapes, raw strings, f-strings), list / tuple / dict
displays, list and dict comprehensions, conditional `a if c else b`,
`lambda`, `and or not`, chained comparisons, `in / not in`, `is / is not`,
arithmetic and bitwise operators, slicing, calls with `*` and `**` unpacking,
attribute access on builtin values (method calls).

Not in the subset (a syntax error): `class`, `finally`, `with`, `yield` and
generators, `async`, decorators, `import` of user modules, `set` and `{a, b}`,
`bytes`, generator expressions, complex numbers, the walrus operator, `match`, type annotations,
`print(..., file=...)`, `exec`/`eval`, `*` in assignment targets.

## 3. Values and builtins

Types: `NoneType`, `bool`, `int`, `float`, `str`, `list`, `tuple`, `dict`
(insertion ordered), `range`, functions and builtin functions.

- **Truthiness** is Python's: `0`, `0.0`, `""`, `[]`, `()`, `{}`, `None`,
  `False` are false.
- **Equality** is structural for lists, tuples and dicts; `1 == 1.0`.
- **Strings** are sequences of Unicode code points. Proposed representation:
  a Java `String` plus a "BMP only" flag, so that indexing is O(1) in the
  common case and falls back to code point scanning otherwise.
- **Integers** are 32-bit (see deviations). `/` is true division, `//` and
  `%` floor, `**` with a negative exponent gives a float.
- **Functions** return one value; `return a, b` returns a tuple. No multiple
  results, no varargs registers.

Builtins: `print len range str repr int float bool list tuple dict abs min
max sum sorted reversed enumerate zip map filter any all isinstance type
round divmod pow ord chr hex oct bin`. `int float str bool list tuple dict
range` and the exception classes are values: they can be called, compared
with `type(x)` and given to `isinstance`. `map`, `filter`, `zip`, `enumerate`
and `reversed` may return lists instead of lazy iterators (always consumed
with `list()` or a `for` loop in the tests).

Methods: `str` (`upper lower strip lstrip rstrip split join replace find
rfind index count startswith endswith isdigit isalpha isalnum isspace
isupper islower ljust rjust center zfill splitlines format`), `list` (`append extend insert
pop remove index count sort reverse clear copy`), `dict` (`get keys values
items pop update setdefault clear copy`), `tuple` (`index count`). The `%`
operator on strings and f-strings are supported with the usual conversions
(`s r d i x X o f e g c %`, width, precision, flags).

Module `math`: `sqrt floor ceil fabs fmod sin cos tan asin acos atan atan2
exp log log10 pow pi e inf nan isnan isinf trunc`.

Exceptions: `BaseException > Exception > {ArithmeticError > {ZeroDivisionError,
OverflowError}, LookupError > {KeyError, IndexError}, ValueError, TypeError,
AssertionError, AttributeError, NameError, RuntimeError > RecursionError}`.
`str(e)` and `e.args` work. No user-defined exception classes (there are no
classes). Exceeding the call depth raises `RecursionError`, which can be
caught. Running out of instructions raises an execution-limit error that
`except` **cannot** catch, so a `try` around an endless loop does not defeat
the limit.

## 4. Deviations from CPython

Tests that exercise these live in `deviations/` and are not run under CPython.

- `int` is 32-bit; an overflow raises `OverflowError`.
- Instruction and call depth limits (not catchable).
- Error messages differ; tests compare the exception type, not the text.
- `hash`, `id`: not provided. `sum` of floats is a plain left-to-right sum
  (CPython 3.12+ compensates); the tests avoid cases where this shows.
- `1 << 31` and any other result outside 32 bits raise `OverflowError`.

## 5. Pipeline

```
source -> PyLexer -> PyParser -> AST -> Resolver -> CodeGen -> Prototype
```

- **PyLexer**: INDENT / DEDENT tokens, no newline inside brackets, tabs
  rejected, triple quotes, string prefixes (`r`, `f`), number literals.
- **PyParser**: recursive descent into an AST. No single pass: conditional
  expressions and comprehensions put an expression before the part that
  decides how to compile it, and nested functions can refer to a local that
  is assigned later in the enclosing function.
- **Resolver**: one pass over the AST per function. A name assigned anywhere
  in a function is local (unless declared `global` / `nonlocal`); other names
  are free variables (upvalue if an enclosing function owns them, else a
  global). Comprehensions get their own scope.
- **CodeGen**: emits registers-based code, reusing the ideas of `FuncState`
  (register allocation, jump lists, constant table).
- `PyC implements Globals.Compiler`, installed like `LuaC` today.

## 6. Instruction set (direction)

From the Lua 5.3 set, by adaptation:

| Lua | Python subset |
|---|---|
| `TEST`, `TESTSET` | truthiness through one `isTrue` function |
| `GETTABLE`, `SETTABLE` | `GETITEM`, `SETITEM` (0-based, negative indices, dict keys) |
| new | `SLICE`, `IN`, `DELITEM` |
| `NEWTABLE`, `SETLIST` | `NEWLIST`, `NEWTUPLE`, `NEWDICT`, `SETLIST` kept for batches |
| `GETTABUP`, `SETTABUP`, `_ENV` | `GETGLOBAL`, `SETGLOBAL` (constant name) |
| `SELF` | kept, resolves methods of builtin types |
| `TFORCALL`, `TFORLOOP` | `GETITER`, `FORITER` (iterator is a Java object) |
| `FORPREP`, `FORLOOP` | kept as a fast path when the iterable is a `range` |
| `CALL` (variable results) | fixed single result; `CALLKW` for keyword arguments, `CALLEX` for `*` / `**` |
| `VARARG`, `TAILCALL` | removed |
| `CONCAT`, `LEN` | removed (`+`, `len()`) |
| arithmetic | overloaded by type (`str + str`, `list + list`, `str * int`), plus `FLOORDIV` |
| new | no `SETUP`-style opcode: handler table in the `Prototype` |

**Instruction counter.** Incremented on backward jumps and calls; when the
limit is reached the VM raises an error that is not catchable by `except`.

**Exceptions.** A handler table in the `Prototype`: `[startpc, endpc) ->
handlerpc, register`. The interpreter loop catches the Java error, looks for a
handler covering the current `pc`, and jumps to it; no cost when nothing is
thrown. `try/except/else` and `raise` only; `finally` and `with` would need
code duplication at every exit and are out of scope.

## 7. Tests

- Test scripts are Python files under `src/test/resources/python`.
- `tools/cpython_check.py` runs each script with CPython; a script that
  prints has its output stored in `<name>.out` (`--update` writes it), a
  script that only asserts must exit with status 0. Scripts in `deviations/`
  are skipped.
- `language/limits.py` is generated by `tools/gen_limits.py`.
- The Java tests (`ResourcesTestCase` and friends) will compile each script
  with `PyC`, run it, and compare the output with the `.out` file.
- Before the compiler exists, the parser can already be tested on all the
  scripts (syntax only), and the AST compared to the one given by CPython's
  `ast` module for the same file.

## 8. Syntax tree and parser (done)

Package `org.luaj.vm2.python`:

- `PyLexer` / `Token`: indentation, brackets, continuation lines, numbers
  (decimal, hex, octal, binary, floats, underscores), strings with prefixes
  `r f u rf fr`, single and triple quotes. Tabs are rejected.
- `PyParser`: recursive descent, nesting limited to 200 levels (a hostile
  input gives a syntax error, never a stack overflow). Constructs outside the
  subset are syntax errors with an explicit message.
- `Node`: generic tree node shaped like CPython's `ast` (same names, same
  fields, same order). Integers are `Long`, floats `Double`, `None` is `null`.
- `AstDump`: text form of a tree. `tools/dump_ast.py` writes the same format
  for CPython's tree.

`PyParserTest` checks that both trees are identical: for the syntax files of
`src/test/resources/python-ast` (reference dumps stored in `.ast` files) and,
when `python3` is available, for every test script of
`src/test/resources/python`. It also checks the error cases and that damaged
input only produces `PySyntaxError`.

Not done at parse time (the resolver and the code generator will report them):
duplicate parameter names, `break` / `continue` outside a loop, `return`
outside a function, attribute assignment, `raise ... from`, integers outside
32 bits, `global` / `nonlocal` misuse.

## 9. Order of work

1. Test scripts as valid Python, checked with CPython. **Done.**
2. `PyLexer`, `PyParser`, AST; test: parse everything, compare with `ast`.
   **Done.**
3. `Resolver`, then `CodeGen` on a first small instruction set, running on
   the existing `LuaValue` values to get something executing.
4. New value types (list, dict, tuple, str), new opcodes, truthiness.
5. Exceptions, instruction counter, builtins and methods.
