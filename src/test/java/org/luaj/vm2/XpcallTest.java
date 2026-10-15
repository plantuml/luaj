package org.luaj.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaEngine;

/** The message handler of xpcall: called for every error that reaches it, with the error value itself. */
class XpcallTest {

	private static void check(String expected, String code) {
		LuaValue r = LuaEngine.newGlobals().load(code, "t").call();
		assertEquals(expected, r.tojstring(), code);
	}

	@Test
	void errorWithLevelZero() {
		check("false handled boom", "local ok, m = xpcall(function() error('boom', 0) end, function(m) return 'handled ' .. m end) return tostring(ok) .. ' ' .. m");
	}

	@Test
	void errorWithPosition() {
		check("false handled t:1: boom", "local ok, m = xpcall(function() error('boom') end, function(m) return 'handled ' .. m end) return tostring(ok) .. ' ' .. m");
	}

	@Test
	void errorValueIsNotConvertedToAString() {
		check("table 7", "local ok, e = xpcall(function() error({code = 7}) end, function(e) return e end) return type(e) .. ' ' .. e.code");
		check("table 7", "local ok, e = xpcall(function() error({code = 7}, 0) end, function(e) return e end) return type(e) .. ' ' .. e.code");
		check("true 8", "local ok, e = xpcall(function() error({code = 7}) end, function(e) e.code = 8 return e end) return tostring(type(e) == 'table') .. ' ' .. e.code");
	}

	@Test
	void handlerResultCanBeAnyValue() {
		check("table 3", "local ok, e = xpcall(function() error('x') end, function(m) return {n = 3} end) return type(e) .. ' ' .. e.n");
		check("number 42", "local ok, e = xpcall(function() error('x', 0) end, function(m) return 42 end) return type(e) .. ' ' .. e");
	}

	@Test
	void errorRaisedByAJavaFunctionCalledDirectly() {
		check("false handled boom", "local ok, m = xpcall(error, function(m) return 'handled ' .. m end, 'boom', 0) return tostring(ok) .. ' ' .. m");
	}

	@Test
	void runtimeErrors() {
		check("true", "local ok, m = xpcall(function() local x = nil; return x.y end, function(m) return 'H:' .. m end) return tostring(m:find('^H:t:1: attempt to index') ~= nil)");
	}

	@Test
	void handlerRunsOnlyOnce() {
		check("1", "local n = 0 xpcall(function() local function f() error('x', 0) end local function g() f() end g() end, function(m) n = n + 1 return m end) return n");
	}

	@Test
	void pcallInsideXpcallDoesNotCallTheHandler() {
		check("true inner 0", "local n = 0 local ok, m = xpcall(function() return pcall(error, 'inner', 0) end, function(m) n = n + 1 return m end) "
				+ "local ok2, m2 = xpcall(function() return pcall(function() error('inner', 0) end) end, function(m) n = n + 1 return m end) return tostring(ok2 == true and m2 == false) .. ' ' .. 'inner' .. ' ' .. n");
	}

	@Test
	void handlerThatFails() {
		check("false error in error handling", "local ok, m = xpcall(function() error('x') end, function(m) error('again') end) return tostring(ok) .. ' ' .. m");
	}

	@Test
	void noErrorNoHandler() {
		check("true 3", "local ok, v = xpcall(function() return 3 end, function(m) return 'no' end) return tostring(ok) .. ' ' .. v");
	}
}
