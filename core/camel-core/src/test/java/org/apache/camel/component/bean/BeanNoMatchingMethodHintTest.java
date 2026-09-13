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
package org.apache.camel.component.bean;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-24703: a bean with several methods and no method name must say which methods it has and how to choose one.
 */
public class BeanNoMatchingMethodHintTest extends ContextTestSupport {

    @Test
    public void testHintNamesTheMethods() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", "hi"));
        String msg = e.getCause().getMessage();
        assertTrue(msg.contains("No method invocation could be created"), msg);
        assertTrue(msg.contains("the bean has the methods addObjects, getLeakedObjectCount"), msg);
        assertTrue(msg.contains("method: <name>"), msg);
    }

    @Test
    public void testSeveralParametersHint() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:two", "hi"));
        String msg = e.getCause().getMessage();
        assertTrue(msg.contains("the method takes 2 parameters and only the message body is bound by default"), msg);
        assertTrue(msg.contains("method: \"format(${body}, 1)\""), msg);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").bean(new TwoMethods());
                from("direct:two").bean(new TwoParams(), "format");
            }
        };
    }

    public static class TwoParams {
        public String format(String body, int times) {
            return body.repeat(times);
        }
    }

    public static class TwoMethods {
        public int addObjects() {
            return 1;
        }

        public int getLeakedObjectCount() {
            return 2;
        }
    }
}
