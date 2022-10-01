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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class JoorBeanInjectVarTest extends CamelTestSupport {

    @BindToRegistry("myEcho")
    private MyEchoBean myEchoBean = new MyEchoBean();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .transform().joor("var bean = #bean:myEcho; return 'Hello ' + bean.echo(bodyAs(String))")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testHello() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello CamelCamel", "Hello WorldWorld");

        template.sendBody("direct:start", "Camel");
        template.sendBody("direct:start", "World");

        MockEndpoint.assertIsSatisfied(context);
    }

}
