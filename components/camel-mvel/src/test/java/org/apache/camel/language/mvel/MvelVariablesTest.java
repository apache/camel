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
package org.apache.camel.language.mvel;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.LanguageTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Exchange variables are exposed on the root object as {@code variables}, {@code getVariable(name)} and
 * {@code getVariable(name, type)}, mirroring {@code headers} and {@code getHeader(...)}.
 */
class MvelVariablesTest extends LanguageTestSupport {

    @Test
    void variablesAreBound() {
        exchange.setVariable("foo", "bar");
        exchange.setVariable("num", 5);

        assertExpression("variables.foo", "bar");
        assertExpression("variables['foo']", "bar");
        assertExpression("getVariable('foo')", "bar");
        assertExpression("variables.num + 2", 7);
        assertExpression("getVariable('missing')", null);
        assertPredicate("variables.foo == 'bar'");
        assertPredicate("getVariable('foo') == 'baz'", false);
    }

    @Test
    void typedVariableGetterConverts() {
        exchange.setVariable("num", "123");

        assertExpression("getVariable('num', Integer) + 1", 124);
        assertPredicate("getVariable('num', Integer) > 100");
    }

    @Test
    void globalVariableThroughGetter() {
        exchange.setVariable("global:greeting", "Hi");

        assertExpression("getVariable('global:greeting')", "Hi");
    }

    @Test
    void variablesSetInRoute() throws Exception {
        getMockEndpoint("mock:camel").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:other").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("direct:start", "Camel", "greeting", "Hello");
        template.sendBodyAndHeader("direct:start", "World", "greeting", "Hello");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setVariable("name", body())
                        .setVariable("greeting", header("greeting"))
                        .setBody().mvel("variables.greeting + ' ' + getVariable('name')")
                        .choice()
                        .when().mvel("variables.name == 'Camel'").to("mock:camel")
                        .otherwise().to("mock:other");
            }
        };
    }

    @Override
    protected String getLanguageName() {
        return "mvel";
    }
}
