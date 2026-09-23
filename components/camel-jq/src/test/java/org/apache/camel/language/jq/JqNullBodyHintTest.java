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
package org.apache.camel.language.jq;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A jq step that finds nothing to evaluate must say the body is null and how to load one, instead of only naming the
 * type it wanted (CAMEL-24838).
 */
public class JqNullBodyHintTest extends JqTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:body").transform().jq(".foo").to("mock:result");

                from("direct:variable")
                        .transform(expression().jq().expression(".foo").source("variable:input").end())
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testNullBodySaysTheBodyIsNullAndHowToLoadOne() {
        String message = rootCauseMessage(assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:body", null)));

        assertTrue(message.contains("No body available of type: com.fasterxml.jackson.databind.JsonNode"),
                "should keep the original message, but was: " + message);
        assertTrue(message.contains("the jq expression got no message body to evaluate, the body is null"),
                "should say the body is null, but was: " + message);
        assertTrue(message.contains("constant: resource:file:"),
                "should show the form for a known file, but was: " + message);
        assertTrue(message.contains("poll:"),
                "should offer poll for a file that is not known in advance, but was: " + message);

        assertTrue(message.contains("has the body of its caller"),
                "should say why the body is null in a direct: route, but was: " + message);
    }

    @Test
    public void testNullVariableSourceNamesTheSource() {
        String message = rootCauseMessage(assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:variable", node("foo", "bar"))));

        assertTrue(message.contains("got no input from variable(input) to evaluate, it is null"),
                "should name the variable source, not the body, but was: " + message);
    }

    private static String rootCauseMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }
}
