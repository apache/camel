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

import java.nio.file.Paths;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.NamedNode;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class NestedChoiceWithEndChoiceIssueTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.getCamelContextExtension().addContextPlugin(NodeIdFactory.class, buildNodeIdFactory());
        return ctx;
    }

    private static NodeIdFactory buildNodeIdFactory() {
        return new NodeIdFactory() {
            @Override
            public String createId(NamedNode definition) {
                return definition.getShortName(); // do not use counter
            }
        };
    }

    @Test
    public void testNestedChoiceOtherwise() throws Exception {
        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context,
                context.getRouteDefinition("myRoute"));
        assertNotNull(xml);
        log.info(xml);

        String expected
                = IOHelper.stripLineComments(
                        Paths.get("src/test/resources/org/apache/camel/processor/NestedChoiceWithEndChoiceIssueTest.xml"), "#",
                        true);
        expected = StringHelper.after(expected, "-->");
        Assertions.assertEquals(expected, "\n" + xml + "\n");

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("2");
        getMockEndpoint("mock:result").expectedHeaderReceived("count", "1000");

        template.sendBodyAndHeader("direct:start", 1, "count", 1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:test").routeId("myRoute")
                    .choice()
                        .when(simple("${header.count} < 1000 && ${body} == 0"))
                            .setHeader("count", simple("${header.count}++"))
                            .setBody(constant(1))
                            .log("First when. Header is:${header.count} Body is:${body}")
                            .to("direct:test")
                        .when(simple("${header.count} < 1000 && ${body} == 1"))
                            .setHeader("count", simple("${header.count}++"))
                            .setBody().constant(2)
                            .log("Second when. Header is:${header.count} Body is:${body}")
                            .to("direct:test")
                        .when(simple("${header.count} < 1000 && ${body} == 2"))
                            .setHeader("count").simple("${header.count}++")
                            .setBody(constant(0))
                            .choice()
                                .when(simple("${header.count} < 500"))
                                    .log("Third when and small header. Header is:${header.count} Body is:${body}")
                                .when(simple("${header.count} < 900"))
                                    .log("Third when and big header. Header is:${header.count} Body is:${body}")
                                .otherwise()
                                    .log("Third when and header over 900. Header is:${header.count} Body is:${body}")
                                    .choice()
                                        .when(simple("${header.count} == 996"))
                                            .log("Deep choice log. Header is:${header.count}")
                                            .setHeader("count", constant(998))
                                        .end().endChoice()
                                .end()
                            .to("direct:test")
                        .endChoice()
                        .otherwise()
                            .log("Header is:${header.count}")
                            .log("Final Body is:${body}")
                        .end();

                from("direct:start").routeId("start")
                        .to("direct:test")
                        .to("mock:result");
            }
        };
    }
}
