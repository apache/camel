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

import java.util.concurrent.CompletionStage;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeanNullCompletionStageTest extends ContextTestSupport {

    @Test
    public void testNullCompletionStageGivesDescriptiveError() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", "Hello"));

        RuntimeCamelException cause = assertInstanceOf(RuntimeCamelException.class, e.getCause());
        assertTrue(cause.getMessage().contains("returned null CompletionStage"),
                "Error should mention null CompletionStage");
        assertTrue(cause.getMessage().contains("doSomething"),
                "Error should mention the method name");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .bean(MyNullFutureBean.class)
                        .to("mock:result");
            }
        };
    }

    @SuppressWarnings("unused")
    public static class MyNullFutureBean {
        public CompletionStage<String> doSomething(String body) {
            return null;
        }
    }
}
