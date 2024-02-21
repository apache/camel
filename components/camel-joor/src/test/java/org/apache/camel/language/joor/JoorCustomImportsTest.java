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
package org.apache.camel.language.joor;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class JoorCustomImportsTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        context.getRegistry().bind("MyConfig", ""
                                               + "import org.apache.camel.language.joor.MyUser;\n"
                                               + "import static org.apache.camel.language.joor.JoorCustomImportsTest.echo;");

        JoorLanguage joor = (JoorLanguage) context.resolveLanguage("joor");
        joor.setConfigResource("ref:MyConfig");
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        // must return null from script as its a void operator, but the joor language expects a returned value
                        .script(joor(
                                "var u = new MyUser(); u.setName('Tony'); u.setAge(22); exchange.getMessage().setBody(u); return null;"))
                        .transform().joor("var u = bodyAs(MyUser.class); return echo(u.getName());")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testImport() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("TonyTony");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    public static String echo(String s) {
        return s + s;
    }

}
