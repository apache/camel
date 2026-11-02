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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that an aggregation stylesheet used by {@link XsltSaxonAggregationStrategy} can still resolve an
 * {@code xsl:include} from the classpath, even though the strategy now restricts external resource access
 * ({@code ACCESS_EXTERNAL_STYLESHEET=""}) the same way the {@code xslt-saxon} endpoint does. The restriction targets
 * resources named by the message body, not the stylesheet's own includes, which Camel resolves from the classpath.
 */
class XsltSaxonAggregationStrategyIncludeTest extends CamelTestSupport {

    @Test
    void shouldResolveClasspathIncludeInAggregationStylesheet() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:aggregate", "<item>A</item>");
        template.sendBody("direct:aggregate", "<item>B</item>");

        MockEndpoint.assertIsSatisfied(context);

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue(body.contains("INCLUDED-"),
                "The xsl:include from the classpath should be resolved (named template from the included stylesheet). Got: "
                                               + body);
        assertTrue(body.contains("AB"), "The aggregation should combine both bodies. Got: " + body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:aggregate")
                        .aggregate(constant(true),
                                new XsltSaxonAggregationStrategy(
                                        "org/apache/camel/component/xslt/saxon/aggregate-with-include.xsl"))
                        .completionSize(2)
                        .to("mock:result");
            }
        };
    }
}
