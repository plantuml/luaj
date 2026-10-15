package org.luaj.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LuaEngineTest {

	@Test
	void runReturnsTheValuesOfTheChunk() {
		Globals g = LuaEngine.newGlobals();
		Varargs r = LuaEngine.run(g, "return 1 + 2, utf8.char(65)", "test");
		assertEquals(3, r.arg1().toint());
		assertEquals("A", r.arg(2).tojstring());
	}

	@Test
	void globalsAreIndependent() {
		Globals a = LuaEngine.newGlobals();
		Globals b = LuaEngine.newGlobals();
		assertNotSame(a, b);
		LuaEngine.run(a, "x = 42", "a");
		assertEquals("nil", LuaEngine.run(b, "return tostring(x)", "b").arg1().tojstring());
	}

	@Test
	void compileErrorIsALuaError() {
		Globals g = LuaEngine.newGlobals();
		assertThrows(LuaError.class, () -> LuaEngine.run(g, "return +", "bad"));
	}

	@Test
	void luaMainPassesTheArguments() {
		Globals g = LuaEngine.newGlobals();
		LuaValue chunk = g.load("return select('#', ...), arg.n, ...", "main");
		Varargs r = LuaEngine.luaMain(chunk, new String[] { "a", "b" });
		assertEquals(2, r.arg(1).toint());
		assertEquals(2, r.arg(2).toint());
		assertEquals("b", r.arg(4).tojstring());
	}
}
