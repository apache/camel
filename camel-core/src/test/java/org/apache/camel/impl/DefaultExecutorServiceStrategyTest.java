/**
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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;

/**
 * @version $Revision$
 */
public class DefaultExecutorServiceStrategyTest extends ContextTestSupport {

    public void testGetThreadName() throws Exception {
        String foo = context.getExecutorServiceStrategy().getThreadName("foo");
        String bar = context.getExecutorServiceStrategy().getThreadName("bar");

        assertNotSame(foo, bar);
        assertTrue(foo.startsWith("Camel Thread "));
        assertTrue(foo.endsWith("foo"));
        assertTrue(bar.startsWith("Camel Thread "));
        assertTrue(bar.endsWith("bar"));
    }

    public void testGetThreadNameCustomPattern() throws Exception {
        context.getExecutorServiceStrategy().setThreadNamePattern("#${counter} - ${name}");
        String foo = context.getExecutorServiceStrategy().getThreadName("foo");
        String bar = context.getExecutorServiceStrategy().getThreadName("bar");

        assertNotSame(foo, bar);
        assertTrue(foo.startsWith("#"));
        assertTrue(foo.endsWith(" - foo"));
        assertTrue(bar.startsWith("#"));
        assertTrue(bar.endsWith(" - bar"));
    }

    public void testGetThreadNameCustomPatternNoCounter() throws Exception {
        context.getExecutorServiceStrategy().setThreadNamePattern("Cool ${name}");
        String foo = context.getExecutorServiceStrategy().getThreadName("foo");
        String bar = context.getExecutorServiceStrategy().getThreadName("bar");

        assertNotSame(foo, bar);
        assertEquals("Cool foo", foo);
        assertEquals("Cool bar", bar);
    }

    public void testGetThreadNameCustomPatternInvalid() throws Exception {
        context.getExecutorServiceStrategy().setThreadNamePattern("Cool ${xxx}");
        try {
            context.getExecutorServiceStrategy().getThreadName("foo");
            fail("Should thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Pattern is invalid: Cool ${xxx}", e.getMessage());
        }

        // reset it so we can shutdown properly
        context.getExecutorServiceStrategy().setThreadNamePattern("Camel Thread ${counter} - ${name}");
    }
}
