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

import java.util.Random;

import org.luaj.vm2.LuaDouble;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaConstant;

/**
 * Subclass of {@link LibFunction} which implements the lua standard {@code math}
 * library.
 * <p>
 * Typically, this library is included as part of a call to
 * {@link org.luaj.vm2.lib.jse.JsePlatform#standardGlobals()}, or loaded directly:
 * <pre> {@code
 * Globals globals = new Globals();
 * globals.load(new BaseLib());
 * globals.load(new PackageLib());
 * globals.load(new MathLib());
 * System.out.println( globals.get("math").get("sqrt").call( LuaValue.valueOf(2) ) );
 * } </pre>
 * @see LibFunction
 * @see <a href="http://www.lua.org/manual/5.2/manual.html#6.6">Lua 5.2 Math Lib Reference</a>
 */
public class MathLib extends TwoArgFunction {

	/** Construct a MathLib, which can be initialized by calling it with a
	 * modname string, and a global environment table as arguments using
	 * {@link #call(LuaValue, LuaValue)}. */
	public MathLib() {
	}

	/** Perform one-time initialization on the library by creating a table
	 * containing the library functions, adding that table to the supplied environment,
	 * adding the table to package.loaded, and returning table as the return value.
	 * @param modname the module name supplied if this is loaded via 'require'.
	 * @param env the environment to load into, typically a Globals instance.
	 */
	public LuaValue call(LuaValue modname, LuaValue env) {
		LuaTable math = new LuaTable(0,30);
		math.set("abs", new abs());
		math.set("ceil", new ceil());
		math.set("cos", new cos());
		math.set("deg", new deg());
		math.set("exp", new exp());
		math.set("floor", new floor());
		math.set("fmod", new fmod());
		math.set("frexp", new frexp());
		math.set("huge", LuaDouble.POSINF );
		math.set("ldexp", new ldexp());
		math.set("max", new max());
		math.set("min", new min());
		math.set("modf", new modf());
		math.set("pi", Math.PI );
		math.set("pow", new pow());
		math.set("acos", new acos());
		math.set("asin", new asin());
		math.set("atan", new atan());
		math.set("atan2", new atan2());
		math.set("cosh", new cosh());
		math.set("log", new log());
		math.set("sinh", new sinh());
		math.set("tanh", new tanh());
		random r;
		math.set("random", r = new random());
		math.set("randomseed", new randomseed(r));
		math.set("rad", new rad());
		math.set("sin", new sin());
		math.set("sqrt", new sqrt());
		math.set("tan", new tan());
		env.set("math", math);
		if (!env.get("package").isnil())
			env.get("package").get("loaded").set("math", math);
		return math;
	}
	
	abstract protected static class UnaryOp extends OneArgFunction {
		@Override
		public LuaValue call(LuaValue arg) {
			return valueOf(call(arg.checkdouble()));
		}
		abstract protected double call(double d);
	}

	abstract protected static class BinaryOp extends TwoArgFunction {
		@Override
		public LuaValue call(LuaValue x, LuaValue y) {
			return valueOf(call(x.checkdouble(), y.checkdouble()));
		}

		abstract protected double call(double x, double y);
	}

	static final class abs extends UnaryOp {
		@Override
		protected double call(double d) { return Math.abs(d); }
	}

	static final class ceil extends UnaryOp {
		@Override
		protected double call(double d) { return Math.ceil(d); }
	}

	static final class cos extends UnaryOp {
		@Override
		protected double call(double d) {return Math.cos(d); }
	}

	static final class deg extends UnaryOp {
		@Override
		protected double call(double d) {return Math.toDegrees(d); }
	}

	static final class floor extends UnaryOp {
		@Override
		protected double call(double d) {return Math.floor(d); }
	}

	static final class rad extends UnaryOp {
		@Override
		protected double call(double d) {return Math.toRadians(d); }
	}

	static final class sin extends UnaryOp {
		@Override
		protected double call(double d) {return Math.sin(d); }
	}

	static final class sqrt extends UnaryOp {
		@Override
		protected double call(double d) {return Math.sqrt(d); }
	}

	static final class tan extends UnaryOp {
		@Override
		protected double call(double d) {return Math.tan(d); }
	}

	static final class exp extends UnaryOp {
		@Override
		protected double call(double d) { return Math.exp(d); }
	}

	static final class acos extends UnaryOp {
		@Override
		protected double call(double d) { return Math.acos(d); }
	}

	static final class asin extends UnaryOp {
		@Override
		protected double call(double d) { return Math.asin(d); }
	}

	static final class cosh extends UnaryOp {
		@Override
		protected double call(double d) { return Math.cosh(d); }
	}

	static final class sinh extends UnaryOp {
		@Override
		protected double call(double d) { return Math.sinh(d); }
	}

	static final class tanh extends UnaryOp {
		@Override
		protected double call(double d) { return Math.tanh(d); }
	}

	static final class atan extends TwoArgFunction {
		@Override
		public LuaValue call(LuaValue x, LuaValue y) {
			return valueOf(Math.atan2(x.checkdouble(), y.optdouble(1)));
		}
	}

	static final class atan2 extends TwoArgFunction {
		@Override
		public LuaValue call(LuaValue x, LuaValue y) {
			return valueOf(Math.atan2(x.checkdouble(), y.checkdouble()));
		}
	}

	static final class log extends TwoArgFunction {
		@Override
		public LuaValue call(LuaValue x, LuaValue base) {
			double nat = Math.log(x.checkdouble());
			double b = base.optdouble(Math.E);
			if (b != Math.E)
				nat /= Math.log(b);
			return valueOf(nat);
		}
	}

	static final class fmod extends TwoArgFunction {
		@Override
		public LuaValue call(LuaValue xv, LuaValue yv) {
			if (yv.checkdouble() == 0.0d)
				return LuaDouble.NAN;
			if (xv.islong() && yv.islong()) {
				return valueOf(xv.tolong() % yv.tolong());
			}
			return valueOf(xv.checkdouble() % yv.checkdouble());
		}
	}

	static final class ldexp extends BinaryOp {
		@Override
		protected double call(double x, double y) {
			// This is the behavior on os-x, windows differs in rounding behavior.
			return x*Double.longBitsToDouble((long) y+1023<<52);
		}
	}

	static final class pow extends BinaryOp {
		@Override
		protected double call(double x, double y) {
			return Math.pow(x, y);
		}
	}

	static class frexp extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			double x = args.arg(1).checkdouble();
			if (x == 0)
				return varargsOf(LuaConstant.ZERO, LuaConstant.ZERO);
			long bits = Double.doubleToLongBits(x);
			double m = ((bits & ~(-1L<<52))+(1L<<52))*(bits >= 0? .5/(1L<<52): -.5/(1L<<52));
			double e = ((int) (bits>>52) & 0x7ff)-1022;
			return varargsOf(valueOf(m), valueOf(e));
		}
	}

	static class max extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			LuaValue m = args.arg(1).checknumber();
			for (int i = 2, n = args.narg(); i <= n; ++i) {
				LuaValue v = args.arg(i).checknumber();
				if (m.lt_b(v))
					m = v;
			}
			return m;
		}
	}

	static class min extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			LuaValue m = args.arg(1).checknumber();
			for (int i = 2, n = args.narg(); i <= n; ++i) {
				LuaValue v = args.arg(i).checknumber();
				if (v.lt_b(m))
					m = v;
			}
			return m;
		}
	}

	static class modf extends VarArgFunction {
		@Override
		public Varargs invoke(Varargs args) {
			LuaValue n = args.arg(1);
			/* number is its own integer part, no fractional part */
			if (n.islong())
				return varargsOf(n.tonumber(), valueOf(0.0));
			double x = n.checkdouble();
			/* integer part (rounds toward zero) */
			double intPart = x > 0? Math.floor(x): Math.ceil(x);
			/* fractional part (test needed for inf/-inf) */
			double fracPart = x == intPart? 0.0: x-intPart;
			return varargsOf(valueOf(intPart), valueOf(fracPart));
		}
	}

	static class random extends LibFunction {
		Random random = new Random();

		@Override
		public LuaValue call() {
			return valueOf(random.nextDouble());
		}

		@Override
		public LuaValue call(LuaValue a) {
			int m = a.checkint();
			if (m < 1)
				argumentError(1, "interval is empty");
			return valueOf(1+random.nextInt(m));
		}

		@Override
		public LuaValue call(LuaValue a, LuaValue b) {
			int m = a.checkint();
			int n = b.checkint();
			if (n < m)
				argumentError(2, "interval is empty");
			return valueOf(m+random.nextInt(n+1-m));
		}

	}

	static class randomseed extends OneArgFunction {
		final random random;

		randomseed(random random) {
			this.random = random;
		}

		@Override
		public LuaValue call(LuaValue arg) {
			long seed = arg.checklong();
			random.random = new Random(seed);
			return LuaConstant.NONE;
		}
	}

	/** compute power, used by the arithmetic operator ^ */
	public static LuaValue dpow(double a, double b) {
		return LuaDouble.valueOf(Math.pow(a, b));
	}

	public static double dpow_d(double a, double b) {
		return Math.pow(a, b);
	}

}
