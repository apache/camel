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

package org.apache.camel.util;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ExceptionHelper;
import org.junit.jupiter.api.Test;

public class NoClassDefFoundErrorWrapExceptionTest extends ContextTestSupport {

    @Test
    public void testNoClassDef() {
        try {
            template.requestBody("seda:start", "Hello World");
            fail("Should throw exception");
        } catch (Exception e) {
            final String s = ExceptionHelper.stackTraceToString(e);
            assertTrue(s.contains("java.lang.LinkageError"));
            assertTrue(s.contains("Cannot do this"));
            assertTrue(s.contains("org.apache.camel.util.ProcessorFail.process"));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start")
                        .process(new ProcessorA())
                        .process(new ProcessorB())
                        .process(new ProcessorFail());
            }
        };
    }
}
