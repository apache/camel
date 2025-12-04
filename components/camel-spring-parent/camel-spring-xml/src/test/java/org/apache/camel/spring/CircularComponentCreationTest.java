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

package org.apache.camel.spring;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CircularComponentCreationTest {
    @Test
    public void testSimple() {
        assertThrows(
                FailedToCreateRouteException.class,
                () -> doTest("org/apache/camel/spring/CircularComponentCreationSimpleTest.xml"));
    }

    @Test
    public void testComplex() {
        assertDoesNotThrow(() -> doTest("org/apache/camel/spring/CircularComponentCreationComplexTest.xml"));
    }

    // *******************************
    // Test implementation
    // *******************************

    private void doTest(String path) throws IOException {
        AbstractXmlApplicationContext applicationContext = null;
        SpringCamelContext ctx = null;
        try {
            applicationContext = new ClassPathXmlApplicationContext(path);
            ctx = new SpringCamelContext(applicationContext);
        } finally {
            IOHelper.close(applicationContext);
            if (ctx != null) {
                ctx.close();
            }
        }
    }
}
