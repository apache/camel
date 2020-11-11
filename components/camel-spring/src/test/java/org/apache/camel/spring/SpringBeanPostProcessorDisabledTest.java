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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

public class SpringBeanPostProcessorDisabledTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/SpringBeanPostProcessorDisabledTest.xml");
    }

    @Test
    public void testDisabled() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "World");
            fail("Should throw exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException iae = (IllegalArgumentException) e.getCause();
            assertEquals("bar is not injected", iae.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public static class MyFoo {

        @Produce("mock:result")
        private ProducerTemplate bar;

        public void somewhere(String input) {
            if (bar == null) {
                throw new IllegalArgumentException("bar is not injected");
            }
            bar.sendBody("Hello " + input);
        }

    }

}
