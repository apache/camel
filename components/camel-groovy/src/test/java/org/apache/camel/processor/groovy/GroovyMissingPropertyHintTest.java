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

import groovy.lang.MissingPropertyException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A groovy script that uses a bean name as a variable, or an unknown variable, gets a MissingPropertyException whose
 * message says where the beans are and what the script variables are (CAMEL-24698).
 */
public class GroovyMissingPropertyHintTest extends CamelTestSupport {

    @Test
    public void beanNameUsedAsVariableIsExplained() {
        context.getRegistry().bind("formatter", new StringBuilder());
        Exception e = assertThrows(Exception.class, () -> template.sendBody("direct:bean", "x"));
        MissingPropertyException cause = assertInstanceOf(MissingPropertyException.class, e.getCause());
        assertTrue(cause.getMessage().contains("'formatter' is a bean in the registry, not a script variable"),
                cause.getMessage());
        assertTrue(cause.getMessage().contains("bean: {ref: formatter}"), cause.getMessage());
    }

    @Test
    public void unknownVariableListsTheScriptVariables() {
        Exception e = assertThrows(Exception.class, () -> template.sendBody("direct:unknown", "x"));
        MissingPropertyException cause = assertInstanceOf(MissingPropertyException.class, e.getCause());
        assertTrue(cause.getMessage().contains("the script variables are exchange, message, body, headers"),
                cause.getMessage());
    }

    @Test
    public void messageIsAScriptVariableAsTheHintSays() {
        String out = template.requestBodyAndHeader("direct:message", "World", "name", "Hello", String.class);
        assertEquals("Hello World", out);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:bean").transform().groovy("formatter.append(body)");
                from("direct:unknown").transform().groovy("nosuch.toUpperCase()");
                from("direct:message").transform().groovy("message.getHeader('name') + ' ' + message.body");
            }
        };
    }
}
