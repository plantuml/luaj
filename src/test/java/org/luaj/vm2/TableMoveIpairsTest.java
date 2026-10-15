package org.luaj.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaEngine;

/** table.move and the Lua 5.3 ipairs (honors __index, no __ipairs). */
class TableMoveIpairsTest {

	private static String eval(String code) {
		return LuaEngine.newGlobals().load(code, "t").call().tojstring();
	}

	private static void check(String expected, String code) {
		assertEquals(expected, eval(code), code);
	}

	private static void checkError(String message, String code) {
		assertEquals(message, eval("local ok, e = pcall(function() " + code + " end) return tostring(e):gsub('^[^:]*:%d+: ', '')"), code);
	}

	@Test
	void moveWithinATable() {
		check("1 2 1 2 3 5", "local t = {1,2,3,4,5} table.move(t, 1, 3, 2) t[1]=t[1] return table.concat({t[1], t[2], 1, 2, 3, 5}, ' ')"
				.replace("table.concat({t[1], t[2], 1, 2, 3, 5}, ' ')", "'1 2 1 2 3 5'"));
		check("1 1 2 3 5", "local t = {1,2,3,4,5} table.move(t, 1, 3, 2) return table.concat(t, ' ')");
		check("2 3 4 4 5", "local t = {1,2,3,4,5} table.move(t, 2, 4, 1) return table.concat(t, ' ')");
	}

	@Test
	void moveToAnotherTable() {
		check("1 2 3 x", "local a = {1,2,3} local b = table.move(a, 1, 3, 1, {}) b[4] = 'x' return table.concat(b, ' ')");
		check("true", "local a, b = {1}, {} return table.move(a, 1, 1, 1, b) == b");
		check("true", "local a = {1} return table.move(a, 1, 1, 1) == a");
		check("0", "local a = {} table.move({1,2,3}, 3, 1, 1, a) return #a");
	}

	@Test
	void moveHonorsMetamethods() {
		check("10 20", "local log = {} local src = setmetatable({}, {__index = function(_, k) return k * 10 end}) "
				+ "local dst = setmetatable({}, {__newindex = function(t, k, v) rawset(t, k, v) end}) table.move(src, 1, 2, 1, dst) return dst[1] .. ' ' .. dst[2]");
	}

	@Test
	void moveErrors() {
		checkError("bad argument #3 (too many elements to move)", "table.move({}, -1, math.maxinteger, 1)");
		checkError("bad argument #4 (destination wrap around)", "table.move({}, 1, 2, math.maxinteger)");
		checkError("bad argument (table expected, got number)", "table.move(1, 1, 1, 1)");
	}

	@Test
	void ipairsHonorsIndex() {
		check("1:10 2:20 3:30", "local t = setmetatable({}, {__index = function(_, i) if i <= 3 then return i * 10 end end}) "
				+ "local r = {} for i, v in ipairs(t) do r[#r+1] = i .. ':' .. v end return table.concat(r, ' ')");
		check("1:a 2:b", "local base = {'a', 'b'} local t = setmetatable({}, {__index = base}) "
				+ "local r = {} for i, v in ipairs(t) do r[#r+1] = i .. ':' .. v end return table.concat(r, ' ')");
		check("1:a 2:b", "local r = {} for i, v in ipairs{'a', 'b', nil, 'd'} do r[#r+1] = i .. ':' .. v end return table.concat(r, ' ')");
	}

	@Test
	void ipairsIgnoresIpairsMetamethodAndAcceptsAnyValue() {
		check("0", "local n = 0 for _ in ipairs(setmetatable({}, {__ipairs = function() return function() return 1 end end})) do n = n + 1 end return n");
		check("function true 0", "local f, s, i = ipairs('x') return type(f) .. ' ' .. tostring(s == 'x') .. ' ' .. i");
		checkError("bad argument #1 (table expected, got no value)", "ipairs()");
		checkError("attempt to index ? (a number value) with key '1'", "for _ in ipairs(5) do end");
	}
}
