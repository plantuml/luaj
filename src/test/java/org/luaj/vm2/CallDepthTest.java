package org.luaj.vm2;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.lib.jse.JsePlatform;

class CallDepthTest {

	private Globals globals() {
		return JsePlatform.standardGlobals();
	}

	@Test
	void infiniteRecursionIsACatchableLuaError() {
		Globals g = globals();
		LuaValue r = g.load("local function f() return 1 + f() end local ok, e = pcall(f) return tostring(ok) .. '|' .. tostring(e)", "t").call();
		assertTrue(r.tojstring().startsWith("false|"), r.tojstring());
		assertTrue(r.tojstring().contains("stack overflow"), r.tojstring());
	}

	@Test
	void uncaughtRecursionThrowsLuaErrorNotStackOverflowError() {
		Globals g = globals();
		LuaError e = assertThrows(LuaError.class, () -> g.load("local function f() return 1 + f() end f()", "t").call());
		assertTrue(e.getMessage().contains("stack overflow"));
	}

	@Test
	void counterIsRestoredAfterError() {
		Globals g = globals();
		g.load("local function f() return 1 + f() end pcall(f)", "t").call();
		assertEquals(0, g.callDepth);
		LuaValue r = g.load("local function f(n) if n == 0 then return 0 end return 1 + f(n-1) end return f(100)", "t").call();
		assertEquals(100, r.toint());
	}

	@Test
	void tailCallsAreNotLimited() {
		Globals g = globals();
		LuaValue r = g.load("local function f(n) if n == 0 then return 'done' end return f(n-1) end return f(100000)", "t").call();
		assertEquals("done", r.tojstring());
	}

	@Test
	void limitIsConfigurable() {
		Globals g = globals();
		g.maxCallDepth = 1000;
		String src = "local function f(n) if n == 0 then return 0 end return 1 + f(n-1) end return f(500)";
		assertEquals(500, g.load(src, "t").call().toint());
		g.maxCallDepth = 50;
		assertThrows(LuaError.class, () -> g.load(src, "t").call());
	}

	@Test
	void errorHandlerStillRunsAtTheLimit() {
		Globals g = globals();
		LuaValue r = g.load("local function f() return 1 + f() end local ok, e = xpcall(f, function(m) return 'handled' end) return e", "t").call();
		assertEquals("handled", r.tojstring());
	}
}
