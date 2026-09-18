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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SplitDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-24698: a split (or any expression node) whose expression was never set, as a YAML file with only options
 * produces, must say that it needs an expression instead of "Unsupported definition: null".
 */
public class SplitWithoutExpressionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSplitWithoutExpression() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                RouteDefinition route = from("direct:start");
                SplitDefinition split = new SplitDefinition();
                split.setId("mySplit");
                route.addOutput(split);
                split.to("mock:result");
            }
        });

        FailedToCreateRouteException e = assertThrows(FailedToCreateRouteException.class, () -> context.start());
        String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        assertTrue(msg.startsWith("No expression: the EIP needs an expression"), msg);
    }
}
