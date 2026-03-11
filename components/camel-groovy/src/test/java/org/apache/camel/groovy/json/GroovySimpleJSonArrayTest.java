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
package org.apache.camel.groovy.json;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.groovy.json.internal.LazyMap;
import org.junit.jupiter.api.Test;

public class GroovySimpleJSonArrayTest extends CamelTestSupport {

    private static final String COUNTRIES
            = """
                    {
                      "countries": [ "Denmark", "Sweden", "Norway" ]
                    }
                    """;

    @Test
    public void testGroovySimpleJsonPath() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).body().isInstanceOf(LazyMap.class); // groovy json object
        getMockEndpoint("mock:result").expectedHeaderReceived("c1", "Denmark");
        getMockEndpoint("mock:result").expectedHeaderReceived("c2", "Sweden");
        getMockEndpoint("mock:result").expectedHeaderReceived("c3", "Norway");

        template.sendBody("direct:start", COUNTRIES);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .unmarshal().groovyJson()
                        .setHeader("c1", simple("${simpleJsonpath(countries[0])}"))
                        .setHeader("c2", simple("${simpleJsonpath(countries[1])}"))
                        .setHeader("c3", simple("${simpleJsonpath(countries[2])}"))
                        .to("mock:result");
            }
        };
    }
}
