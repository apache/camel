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
package org.apache.camel.component.jslt;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

/**
 * Tests using variables used in headers
 */
public class JsltVariablesTest extends CamelTestSupport {

    @Test
    public void testWithVariables() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived(
                IOHelper.loadText(
                        ResourceHelper.resolveMandatoryResourceAsInputStream(
                                context, "org/apache/camel/component/jslt/withVariables/output.json"))
                        .trim() // Remove the last newline added by IOHelper.loadText()
        );

        Map<String, Object> headers = new HashMap<>();
        headers.put("published", "2020-05-26T16:00:00+02:00");
        headers.put("type", "Controller");
        //add an infinite recursion value, cannot be serialized with Jackson
        headers.put("infinite", createInfiniteRecursionObject());
        sendBody("direct://start",
                ResourceHelper.resolveMandatoryResourceAsInputStream(
                        context, "org/apache/camel/component/jslt/withVariables/input.json"),
                headers);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testWithVariablesAndProperties() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived(
                IOHelper.loadText(
                        ResourceHelper.resolveMandatoryResourceAsInputStream(
                                context, "org/apache/camel/component/jslt/withVariables/outputWithProperties.json"))
                        .trim() // Remove the last newline added by IOHelper.loadText()
        );

        InputStream body = ResourceHelper.resolveMandatoryResourceAsInputStream(
                context, "org/apache/camel/component/jslt/withVariables/input.json");

        template.send("direct://startWithProperties", exchange -> {
            exchange.getIn().setBody(body);
            exchange.getIn().setHeader("published", "2020-05-26T16:00:00+02:00");
            exchange.getIn().setHeader("type", "Controller");
            // add an infinite recursion value, cannot be serialized with Jackson
            exchange.setProperty("infinite", createInfiniteRecursionObject());
            exchange.setProperty("instance", "559e934f-b32b-47ab-8327-bd50e2bdc029");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start")
                        .to("jslt:org/apache/camel/component/jslt/withVariables/transformation.json")
                        .to("mock:result");

                from("direct://startWithProperties")
                        .to("jslt:org/apache/camel/component/jslt/withVariables/transformationWithProperties.json?allowContextMapAll=true")
                        .to("mock:result");
            }
        };
    }

    private static Master createInfiniteRecursionObject() {
        Master master = new Master();
        Slave slave = new Slave();
        master.slave = slave;
        slave.master = master;
        return master;
    }

    private static class Master {
        private Slave slave;
    }

    private static class Slave {
        private Master master;
    }
}
