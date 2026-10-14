package org.luaj;

import org.junit.jupiter.api.*;
import org.luaj.vm2.*;

public class CompatibilityTest {
    abstract static class CompatibilityTestCase extends PlatformTestCase {
        LuaValue savedStringMetatable;

        @BeforeEach
        @Override
        protected void setUp() {
            savedStringMetatable = LuaString.s_metatable;
            setBaseDir("compatibility");
            super.setUp();
        }

        @AfterEach
        protected void tearDown() {
            LuaNil.s_metatable = null;
            LuaBoolean.s_metatable = null;
            LuaNumber.s_metatable = null;
            LuaFunction.s_metatable = null;
            LuaThread.s_metatable = null;
            LuaString.s_metatable = savedStringMetatable;
        }

        @Test
        void testBaseLib() { runTest("baselib"); }

        @Test
        void testCoroutineLib() { runTest("coroutinelib"); }

        @Test
        void testErrors() { runTest("errors"); }

        @Test
        void testFunctions() { runTest("functions"); }

        @Test
        void testIoLib() { runTest("iolib"); }

        @Test
        void testManyUpvals() { runTest("manyupvals"); }

        @Test
        void testMathLib() { runTest("mathlib"); }

        @Test
        void testMetatags() { runTest("metatags"); }

        @Test
        void testOsLib() { runTest("oslib"); }

        @Test
        void testStringLib() { runTest("stringlib"); }

        @Test
        void testTableLib() { runTest("tablelib"); }

        @Test
        void testTailcalls() { runTest("tailcalls"); }

        @Test
        void testUpvalues() { runTest("upvalues"); }

        @Test
        void testVm() { runTest("vm"); }
    }

    @Nested
    class JseCompatibilityTest extends CompatibilityTestCase {

        @BeforeEach
        @Override
        protected void setUp() {
            setPlatform(PlatformTestCase.PlatformType.JSE);
            System.setProperty("JME", "false");
            super.setUp();
        }
    }
}
