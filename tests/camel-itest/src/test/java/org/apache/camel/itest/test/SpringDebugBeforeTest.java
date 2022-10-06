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
package org.apache.camel.itest.test;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpringDebugBeforeTest extends CamelSpringTestSupport {

    private final List<String> before = new ArrayList<>();

    @Override
    public boolean isUseDebugger() {
        // must enable debugger
        return true;
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/test/SpringDebugBeforeTest.xml");
    }

    @Override
    protected void debugBefore(
            Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String label) {
        before.add(id);
    }

    @Test
    void testDebugBefore() throws Exception {
        getMockEndpoint("mock:SpringDebugBeforeTestResult").expectedMessageCount(1);

        template.sendBody("direct:SpringDebugBeforeTestStart", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(2, before.size());

        // The ID is not truly deterministic and may be appended a number. To avoid issues with the
        // IDs receiving a different number other than 1 (as is the case when running multiple tests)
        // checks only for the preceding ID string for each of the declared routes.
        assertTrue(before.get(0).startsWith("log"));
        assertTrue(before.get(1).startsWith("to"));

    }
}
