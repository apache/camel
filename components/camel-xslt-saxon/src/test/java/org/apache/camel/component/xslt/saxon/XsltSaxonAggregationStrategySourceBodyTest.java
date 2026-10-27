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
package org.apache.camel.component.xslt.saxon;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.xml.StringSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks how {@link XsltSaxonAggregationStrategy} parses a message body that is already a
 * {@link javax.xml.transform.Source} when it aggregates it. Such a body reaches Saxon still to be parsed, unlike the
 * other body types which Camel converts first, so the strategy must parse it with the same restrictions the
 * {@code xslt-saxon} endpoint applies to its own factory: a reference to a resource named by the body is not resolved
 * from it, while a declaration internal to the document is still applied.
 */
class XsltSaxonAggregationStrategySourceBodyTest extends CamelTestSupport {

    @TempDir
    static Path tmp;

    private static final String SENTINEL = "SENTINELFILECONTENT";

    @Test
    void shouldNotResolveSystemReferenceInAggregatedSourceBody() throws Exception {
        Path file = tmp.resolve("neighbour.txt");
        Files.writeString(file, SENTINEL);

        String firstBody = "<!DOCTYPE item [<!ENTITY ref SYSTEM \"" + file.toUri() + "\">]><item>&ref;</item>";

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:aggregate", new StringSource(firstBody));
        template.sendBody("direct:aggregate", "<item>B</item>");

        MockEndpoint.assertIsSatisfied(context);

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertFalse(body.contains(SENTINEL),
                "The resource named by the aggregated body must not be resolved into the result. Got: " + body);
    }

    @Test
    void shouldStillApplyInternalDeclarationInAggregatedSourceBody() throws Exception {
        String firstBody = "<!DOCTYPE item [<!ENTITY ok \"OKVALUE\">]><item>&ok;</item>";

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:aggregate", new StringSource(firstBody));
        template.sendBody("direct:aggregate", "<item>B</item>");

        MockEndpoint.assertIsSatisfied(context);

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue(body.contains("OKVALUE"),
                "A declaration internal to the document should still be applied. Got: " + body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:aggregate")
                        .aggregate(constant(true),
                                new XsltSaxonAggregationStrategy("org/apache/camel/util/toolbox/aggregate.xsl"))
                        .completionSize(2)
                        .to("mock:result");
            }
        };
    }
}
