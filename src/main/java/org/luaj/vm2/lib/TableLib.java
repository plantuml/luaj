/*******************************************************************************
* Copyright (c) 2009 Luaj.org. All rights reserved.
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in
* all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
* THE SOFTWARE.
******************************************************************************/
package org.luaj.vm2.lib;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaConstant;

/**
 * Subclass of {@link LibFunction} which implements the lua standard {@code table}
 * library.
 * 
 * <p>
 * Typically, this library is included as part of a call to either
 * {@link org.luaj.vm2.LuaEngine#newGlobals()}
 * <pre> {@code
 * Globals globals = LuaEngine.newGlobals();
 * System.out.println( globals.get("table").get("length").call( LuaValue.tableOf() ) );
 * } </pre>
 * <p>
 * To instantiate and use it directly,
 * link it into your globals table via {@link LuaValue#load(LuaValue)} using code such as:
 * <pre> {@code
 * Globals globals = new Globals();
 * globals.load(new BaseLib());
 * globals.load(new PackageLib());
 * globals.load(new TableLib());
 * System.out.println( globals.get("table").get("length").call( LuaValue.tableOf() ) );
 * } </pre>
 * <p>
 * This has been implemented to match as closely as possible the behavior in the corresponding library in C.
 * @see LibFunction
 * @see org.luaj.vm2.LuaEngine
 * @see <a href="http://www.lua.org/manual/5.2/manual.html#6.5">Lua 5.2 Table Lib Reference</a>
 */
public class TableLib extends TwoArgFunction {

	/** Perform one-time initialization on the library by creating a table
	 * containing the library functions, adding that table to the supplied environment,
	 * adding the table to package.loaded, and returning table as the return value.
	 * @param modname the module name supplied if this is loaded via 'require'.
	 * @param env the environment to load into, typically a Globals instance.
	 */
	public LuaValue call(LuaValue modname, LuaValue env) {
		LuaTable table = new LuaTable();
		table.set("concat", new concat());
		table.set("insert", new insert());
		table.set("move", new move());
		table.set("pack", new pack());
		table.set("remove", new remove());
		table.set("sort", new sort());
		table.set("unpack", new unpack());
		env.set("table", table);
		if (!env.get("package").isnil())
			env.get("package").get("loaded").set("table", table);
		return LuaConstant.NIL;
	}
	
	// "concat" (table [, sep [, i [, j]]]) -> string
	static class concat extends TableLibFunction {
		@Override
		public LuaValue call(LuaValue list) {
			return list.checktable().concat(LuaConstant.EMPTY_STRING,1,list.length());
		}

		@Override
		public LuaValue call(LuaValue list, LuaValue sep) {
			return list.checktable().concat(sep.checkstring(), 1, list.length());
		}

		@Override
		public LuaValue call(LuaValue list, LuaValue sep, LuaValue i) {
			return list.checktable().concat(sep.checkstring(), i.checkint(), list.length());
		}

		@Override
		public LuaValue call(LuaValue list, LuaValue sep, LuaValue i, LuaValue j) {
			return list.checktable().concat(sep.checkstring(),i.checkint(),j.checkint());
		}
	}

	// "insert" (table, [pos,] value)
	static class insert extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			switch (args.narg()) {
			case 2: {
				LuaTable table = args.arg(1).checktable();
				table.insert(table.length()+1, args.arg(2));
				return LuaConstant.NONE;
			}
			case 3: {
				LuaTable table = args.arg(1).checktable();
				int pos = args.arg(2).checkint();
				int max = table.length() + 1;
				if (pos < 1 || pos > max)
					argumentError(2, "position out of bounds: " + pos + " not between 1 and " + max);
				table.insert(pos, args.arg(3));
				return LuaConstant.NONE;
			}
			default: {
				return error("wrong number of arguments to 'table.insert': " + args.narg() + " (must be 2 or 3)");
			}
			}
		}
	}
	
	// "move" (a1, f, e, t [,a2]) -> a2 ; reads and writes through __index / __newindex
	static class move extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			LuaTable a1 = args.arg(1).checktable();
			int f = args.checkint(2);
			int e = args.checkint(3);
			int t = args.checkint(4);
			LuaTable tt = args.isnoneornil(5)? a1: args.arg(5).checktable();
			if (e >= f) {
				if (!(f > 0 || e < Integer.MAX_VALUE + f))
					argumentError(3, "too many elements to move");
				long n = (long) e - f + 1;
				if (t > Integer.MAX_VALUE - n + 1)
					argumentError(4, "destination wrap around");
				if (t > e || t <= f || tt != a1) {
					for (int i = 0; i < n; i++)
						tt.set(t + i, a1.get(f + i));
				} else {
					for (int i = (int) n - 1; i >= 0; i--)
						tt.set(t + i, a1.get(f + i));
				}
			}
			return tt;
		}
	}

	// "pack" (...) -> table
	static class pack extends VarArgFunction {
		public Varargs invoke(Varargs args) {
			LuaValue t = tableOf(args, 1);
			t.set("n", args.narg());
			return t;
		}
	}

	// "remove" (table [, pos]) -> removed-ele
	static class remove extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			LuaTable table = args.arg(1).checktable();
			int size = table.length();
			int pos = args.arg(2).optint(size);
			if (pos != size && (pos < 1 || pos > size + 1)) {
				argumentError(2, "position out of bounds: " + pos + " not between 1 and " + (size + 1));
			}
			return table.remove(pos);
		}
	}

	// "sort" (table [, comp])
	static class sort extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			args.arg(1).checktable().sort(
					args.arg(2).isnil() ? LuaConstant.NIL: args.arg(2).checkfunction());
			return LuaConstant.NONE;
		}
	}

	
	// "unpack", // (list [,i [,j]]) -> result1, ...
	static class unpack extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			LuaTable t = args.arg(1).checktable();
			// do not waste resource for calc rawlen if arg3 is not nil
			int len = args.arg(3).isnil() ? t.length() : 0;
			return t.unpack(args.arg(2).optint(1), args.arg(3).optint(len));
		}
	}
}
