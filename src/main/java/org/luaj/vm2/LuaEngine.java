/*******************************************************************************
 * Copyright (c) 2009-2011 Luaj.org. All rights reserved.
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
package org.luaj.vm2;

import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.ResourceFinder;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.Utf8Lib;

/**
 * Entry point of the engine: creates the {@link Globals} a script runs in and
 * runs chunks in them. It does not depend on any platform (it compiles with
 * TeaVM), and it is the one place that decides which libraries a script sees.
 * <p>
 * A simple example:
 * <pre> {@code
 * Globals globals = LuaEngine.newGlobals();
 * Varargs result = LuaEngine.run(globals, "return 1 + 2", "example");
 * } </pre>
 * <p>
 * {@code require} needs a {@link ResourceFinder} in {@link Globals#finder};
 * there is no file system access by default.
 * <p>
 * The globals contain the libraries {@link BaseLib}, {@link PackageLib},
 * {@link TableLib}, {@link StringLib}, {@link Utf8Lib} and {@link MathLib}, and
 * the {@link LuaC} compiler so that source chunks can be loaded.
 *
 * @see Globals
 */
public class LuaEngine {

	private LuaEngine() {
	}

	/**
	 * Creates a new, independent set of globals with the standard libraries.
	 * @return a new {@link Globals}
	 */
	public static Globals newGlobals() {
		Globals globals = new Globals();
		globals.load(new BaseLib());
		globals.load(new PackageLib());
		globals.load(new TableLib());
		globals.load(new StringLib());
		globals.load(new Utf8Lib());
		globals.load(new MathLib());
		LuaC.install(globals);
		return globals;
	}

	/**
	 * Compiles and runs a chunk of source text in the given globals.
	 * @param globals the globals, from {@link #newGlobals()}
	 * @param source the Lua source
	 * @param chunkName the name shown in error messages
	 * @return the values returned by the chunk
	 * @throws LuaError if the chunk does not compile or fails
	 */
	public static Varargs run(Globals globals, String source, String chunkName) {
		return globals.load(source, chunkName).invoke();
	}

	/**
	 * Runs a main chunk with command line style arguments: they are available
	 * as the table {@code arg} and as the {@code ...} of the chunk.
	 * @param mainChunk the chunk
	 * @param args the arguments
	 * @return the values returned by the chunk
	 */
	public static Varargs luaMain(LuaValue mainChunk, String[] args) {
		Globals g = newGlobals();
		int n = args.length;
		LuaValue[] vargs = new LuaValue[args.length];
		for (int i = 0; i < n; ++i)
			vargs[i] = LuaValue.valueOf(args[i]);
		LuaValue arg = LuaValue.listOf(vargs);
		arg.set("n", n);
		g.set("arg", arg);
		mainChunk.initupvalue1(g);
		return mainChunk.invoke(LuaValue.varargsOf(vargs));
	}
}
