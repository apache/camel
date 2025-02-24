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
package org.apache.camel.spring.routebuilder;

import org.apache.camel.Handler;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SpringTemplatedRouteTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/routebuilder/SpringTemplatedRouteTest.xml");
    }

    @Test
    public void testDefineRoute() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());
        assertEquals(2, context.getRoutes().size());
        assertNotNull(context.getRouteDefinition("my-route"));
        getMockEndpoint("mock:barVal").expectedBodiesReceived("-> Hello John!");

        template.sendBody("direct:fooVal", null);

        assertMockEndpointsSatisfied();
    }

    public static class MySpecialBean {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Handler
        public String addName(String body) {
            return String.format("%s %s!", body, name);
        }
    }

    public static class MyScriptBean {

        public MyScriptBean create() {
            return new MyScriptBean();
        }

        @Handler
        public String prefix() {
            return "-> Hello";
        }
    }
}
