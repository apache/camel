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
package org.apache.camel.builder;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.xslt.saxon.XsltSaxonAggregationStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test for the {@link XsltSaxonAggregationStrategy}.
 * <p>
 * Need to use Saxon to get a predictable result: We cannot rely on the JDK's XSLT processor as it can vary across
 * platforms and JDK versions. Also, Xalan does not handle node-set properties well.
 */
public class XsltAggregationStrategyTest extends CamelTestSupport {

    @Test
    public void testXsltAggregationDefaultProperty() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:transformed");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("<?xml version=\"1.0\" encoding=\"UTF-8\"?><item>ABC</item>");

        context.getRouteController().startRoute("route1");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testXsltAggregationUserProperty() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:transformed");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("<?xml version=\"1.0\" encoding=\"UTF-8\"?><item>ABC</item>");

        context.getRouteController().startRoute("route2");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:src/test/resources/org/apache/camel/util/toolbox?noop=true&sortBy=file:name&antInclude=*.xml")
                        .routeId("route1").noAutoStartup()
                        .aggregate(new XsltSaxonAggregationStrategy("org/apache/camel/util/toolbox/aggregate.xsl"))
                        .constant(true)
                        .completionFromBatchConsumer()
                        .log("after aggregate body: ${body}")
                        .to("mock:transformed");

                from("file:src/test/resources/org/apache/camel/util/toolbox?noop=true&sortBy=file:name&antInclude=*.xml")
                        .routeId("route2").noAutoStartup()
                        .aggregate(new XsltSaxonAggregationStrategy("org/apache/camel/util/toolbox/aggregate-user-property.xsl")
                                .withPropertyName("user-property"))
                        .constant(true)
                        .completionFromBatchConsumer()
                        .log("after aggregate body: ${body}")
                        .to("mock:transformed");
            }
        };
    }
}
