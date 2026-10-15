package org.luaj.vm2.lib;

import org.luaj.vm2.LuaConstant;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * A small subset of the standard Lua {@code os} library, for the engine flavor
 * of PlantUML (never for text Lua): {@code os.getenv}, {@code os.clock},
 * {@code os.time} (without argument) and {@code os.exit}. Everything comes from
 * the {@link OsHost} given by the host. There is no {@code os.execute},
 * {@code os.remove}, {@code os.rename}, {@code os.tmpname}, {@code os.date}
 * or {@code os.setlocale}.
 * <p>
 * Usage: {@code globals.load(new OsLib(host))}, or
 * {@link org.luaj.vm2.LuaEngine#newEngineGlobals(LuaFileSystem, OsHost)}.
 *
 * @see OsHost
 */
public class OsLib extends TwoArgFunction {

	private static final int GETENV = 0;
	private static final int CLOCK = 1;
	private static final int TIME = 2;
	private static final int EXIT = 3;

	private final OsHost host;

	/**
	 * @param host the operating system services of the host
	 */
	public OsLib(OsHost host) {
		if (host == null)
			throw new IllegalArgumentException("host");
		this.host = host;
	}

	public LuaValue call(LuaValue modname, LuaValue env) {
		LuaTable t = new LuaTable();
		t.set("getenv", new OsFunction(this, GETENV, "getenv"));
		t.set("clock", new OsFunction(this, CLOCK, "clock"));
		t.set("time", new OsFunction(this, TIME, "time"));
		t.set("exit", new OsFunction(this, EXIT, "exit"));
		env.set("os", t);
		if (!env.get("package").isnil())
			env.get("package").get("loaded").set("os", t);
		return t;
	}

	private static final class OsFunction extends VarArgFunction {
		private final OsLib lib;
		private final int op;

		OsFunction(OsLib lib, int op, String name) {
			this.lib = lib;
			this.op = op;
			this.name = name;
		}

		public Varargs invoke(Varargs args) {
			switch (op) {
			case GETENV: {
				String v = lib.host.getenv(args.checkjstring(1));
				return v == null ? LuaConstant.NIL : LuaValue.valueOf(v);
			}
			case CLOCK:
				return LuaValue.valueOf(lib.host.clock());
			case TIME: {
				if (!args.isnoneornil(1))
					throw new LuaError("os.time: a date table is not supported");
				long t = lib.host.time();
				// Integers have 32 bits: after 2038 the time is a float.
				if (t >= Integer.MIN_VALUE && t <= Integer.MAX_VALUE)
					return LuaValue.valueOf((int) t);
				return LuaValue.valueOf((double) t);
			}
			case EXIT: {
				LuaValue c = args.arg1();
				int status;
				if (c.isnil() || c == LuaConstant.TRUE)
					status = 0;
				else if (c == LuaConstant.FALSE)
					status = 1;
				else
					status = args.optint(1, 0);
				lib.host.exit(status);
				throw new LuaError("os.exit: the host did not exit");
			}
			default:
				return LuaConstant.NONE;
			}
		}
	}
}
