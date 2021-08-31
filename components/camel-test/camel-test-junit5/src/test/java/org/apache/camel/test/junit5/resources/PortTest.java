/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.test.junit5.resources;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SuppressWarnings({ "checkstyle:HideUtilityClassConstructor", "checkstyle:DeclarationOrder" })
public class PortTest {

    @Resources
    public static class ScopeTestTest {

        @AvailablePort
        @TestScope
        int port;

        static int testPort;

        @Test
        void testPort1() {
            assertNotEquals(0, port);
            if (testPort == 0) {
                testPort = port;
            } else {
                assertNotEquals(testPort, port);
            }
        }

        @Test
        void testPort2() {
            assertNotEquals(0, port);
            if (testPort == 0) {
                testPort = port;
            } else {
                assertNotEquals(testPort, port);
            }
        }

    }

    @Resources
    public static class ScopeClassTest {

        @AvailablePort
        @ClassScope
        int port;

        static int classPort;

        @Test
        void testPort1() {
            assertNotEquals(0, port);
            if (classPort == 0) {
                classPort = port;
            } else {
                assertEquals(classPort, port);
            }
        }

        @Test
        void testPort2() {
            assertNotEquals(0, port);
            if (classPort == 0) {
                classPort = port;
            } else {
                assertEquals(classPort, port);
            }
        }

    }

    static int suitePort;

    @Resources
    public static class ScopeSuite1Test {

        @AvailablePort
        @SuiteScope
        int port;

        @Test
        void testPort() {
            assertNotEquals(0, port);
            if (suitePort == 0) {
                suitePort = port;
            } else {
                assertNotEquals(suitePort, port);
            }
        }

    }

    @Resources
    public static class ScopeSuite2Test {

        @AvailablePort
        @SuiteScope
        int port;

        @Test
        void testPort() {
            assertNotEquals(0, port);
            if (suitePort == 0) {
                suitePort = port;
            } else {
                assertNotEquals(suitePort, port);
            }
        }

    }

    @Resources
    public static class ScopeClassStaticTest {

        @AvailablePort
        static int port;

        static int classPort;

        @Test
        void testPort1() {
            assertNotEquals(0, port);
            if (classPort == 0) {
                classPort = port;
            } else {
                assertEquals(classPort, port);
            }
        }

        @Test
        void testPort2() {
            assertNotEquals(0, port);
            if (classPort == 0) {
                classPort = port;
            } else {
                assertEquals(classPort, port);
            }
        }

    }

    static int staticSuitePort;

    @Resources
    public static class ScopeSuite1StaticTest {

        @AvailablePort
        @SuiteScope
        static int port;

        @Test
        void testPort() {
            assertNotEquals(0, port);
            if (staticSuitePort == 0) {
                staticSuitePort = port;
            } else {
                assertNotEquals(staticSuitePort, port);
            }
        }

    }

    @Resources
    public static class ScopeSuite2StaticTest {

        @AvailablePort
        @SuiteScope
        static int port;

        @Test
        void testPort() {
            assertNotEquals(0, port);
            if (staticSuitePort == 0) {
                staticSuitePort = port;
            } else {
                assertNotEquals(staticSuitePort, port);
            }
        }

    }
}
