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
package org.apache.camel.processor.groovy;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.groovy.DefaultGroovyScriptCompiler;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class GroovyCompilerRouteTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        DefaultGroovyScriptCompiler compiler = new DefaultGroovyScriptCompiler();
        compiler.setCamelContext(context);
        // test with file instead of default
        compiler.setScriptPattern("file:src/test/resources/camel-groovy/*");
        context.addService(compiler);

        return context;
    }

    @Test
    public void testCompilerRoute() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("I want to order 2 gauda");

        template.sendBodyAndHeader("direct:start", "Hello World", "amount", 2);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setBody().groovy("""
                                Dude d = new Dude()
                                return d.order(header.amount)
                                """)
                        .to("mock:result");
            }
        };
    }
}
