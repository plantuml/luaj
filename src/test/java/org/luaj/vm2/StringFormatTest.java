package org.luaj.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.lib.jse.JsePlatform;

class StringFormatTest {

	private LuaValue eval(String code) {
		return JsePlatform.standardGlobals().load(code, "t").call();
	}

	@Test
	void referenceValuesForEFG() throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/format/numbers.lua")) {
			String code = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			assertEquals(0, eval(code).toint(), "mismatches in string.format(%e/%f/%g)");
		}
	}

	@Test
	void roundsExactTiesToEven() {
		assertEquals("0.12", eval("return string.format('%.2f', 0.125)").tojstring());
		assertEquals("0.38", eval("return string.format('%.2f', 0.375)").tojstring());
		assertEquals("2", eval("return string.format('%.0f', 2.5)").tojstring());
	}

	@Test
	void nonFiniteValues() {
		assertEquals("inf", eval("return string.format('%f', math.huge)").tojstring());
		assertEquals("-inf", eval("return string.format('%g', -math.huge)").tojstring());
		assertEquals("NAN", eval("return string.format('%G', 0/0)").tojstring());
	}

	@Test
	void mathFunctionsMovedToCore() {
		assertEquals("1", eval("return string.format('%g', math.atan2(1, 1) * 4 / math.pi)").tojstring());
		assertEquals("1024", eval("return string.format('%d', 2 ^ 10)").tojstring());
		assertEquals("3", eval("return string.format('%g', math.log(8, 2))").tojstring());
	}
}
