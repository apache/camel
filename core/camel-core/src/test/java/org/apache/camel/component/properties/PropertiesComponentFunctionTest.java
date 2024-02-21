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
package org.apache.camel.component.properties;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PropertiesComponentFunctionTest extends ContextTestSupport {

    public static final class MyFunction extends ServiceSupport implements PropertiesFunction {

        @Override
        public String getName() {
            return "beer";
        }

        @Override
        public String apply(String remainder) {
            return "mock:" + remainder.toLowerCase();
        }

    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testFunction() throws Exception {
        MyFunction func = new MyFunction();
        PropertiesComponent pc = (PropertiesComponent) context.getPropertiesComponent();
        pc.addPropertiesFunction(func);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:{{sys:os.name}}").to("{{beer:FOO}}").to("{{beer:BAR}}");
            }
        });
        context.start();

        // function should be started by camel
        Assertions.assertTrue(func.isStarted());

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        context.stop();

        // function should be stopped by camel also
        Assertions.assertTrue(func.isStopped());
    }

}
