package org.luaj.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** What a script sees in the globals of {@link LuaEngine#newGlobals()}. */
class SandboxTest {

	private static String eval(String code) {
		return LuaEngine.newGlobals().load(code, "t").call().tojstring();
	}

	@Test
	void noFileOrBytecodeFunctions() {
		assertEquals("nil nil nil", eval("return tostring(dofile) .. ' ' .. tostring(loadfile) .. ' ' .. tostring(string.dump)"));
	}

	@Test
	void collectgarbageDoesNothingAndReturnsZero() {
		assertEquals("0 0 0 0", eval(
				"return collectgarbage() .. ' ' .. collectgarbage('count') .. ' ' .. collectgarbage('collect') .. ' ' .. collectgarbage('step')"));
	}

	@Test
	void binaryChunksAreRefused() {
		assertEquals("nil", eval("local f = load('\\27Lua') return tostring(f)"));
		assertEquals("nil", eval("local f = load('return 1', 'x', 'b') return tostring(f)"));
		assertEquals("1", eval("return load('return 1', 'x', 't')()"));
	}
}
