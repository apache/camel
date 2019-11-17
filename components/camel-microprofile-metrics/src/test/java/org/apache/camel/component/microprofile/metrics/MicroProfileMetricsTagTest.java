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
package org.apache.camel.component.microprofile.metrics;

import java.util.List;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_METRIC_TAGS;

public class MicroProfileMetricsTagTest extends MicroProfileMetricsTestSupport {

    @Test
    public void testMetricTags() {
        template.sendBody("direct:tags", null);
        List<Tag> tags = getMetricTags("test-counter");
        assertEquals(3, tags.size());

        Tag tagCamelContext = tags.get(0);
        assertEquals("camelContext", tagCamelContext.getTagName());
        assertEquals(context.getName(), tagCamelContext.getTagValue());

        Tag tagCheese = tags.get(1);
        assertEquals("cheese", tagCheese.getTagName());
        assertEquals("wine", tagCheese.getTagValue());

        Tag tagFoo = tags.get(2);
        assertEquals("foo", tagFoo.getTagName());
        assertEquals("bar", tagFoo.getTagValue());
    }

    @Test
    public void testMetricTagsFromHeader() {
        template.sendBody("direct:tagsFromHeader", null);
        List<Tag> tags = getMetricTags("test-counter-header");
        assertEquals(3, tags.size());

        Tag tagA = tags.get(0);
        assertEquals("a", tagA.getTagName());
        assertEquals("b", tagA.getTagValue());

        Tag tagC = tags.get(1);
        assertEquals("c", tagC.getTagName());
        assertEquals("d", tagC.getTagValue());

        Tag tagCamelContext = tags.get(2);
        assertEquals("camelContext", tagCamelContext.getTagName());
        assertEquals(context.getName(), tagCamelContext.getTagValue());
    }

    @Test
    public void testMetricTagsFromUriMergeWithHeaderValue() {
        template.sendBodyAndHeader("direct:tags", null, HEADER_METRIC_TAGS, "a=b,c=d");
        List<Tag> tags = getMetricTags("test-counter");
        assertEquals(5, tags.size());

        Tag tagA = tags.get(0);
        assertEquals("a", tagA.getTagName());
        assertEquals("b", tagA.getTagValue());

        Tag tagC = tags.get(1);
        assertEquals("c", tagC.getTagName());
        assertEquals("d", tagC.getTagValue());

        Tag tagCamelContext = tags.get(2);
        assertEquals("camelContext", tagCamelContext.getTagName());
        assertEquals(context.getName(), tagCamelContext.getTagValue());

        Tag tagCheese = tags.get(3);
        assertEquals("cheese", tagCheese.getTagName());
        assertEquals("wine", tagCheese.getTagValue());

        Tag tagFoo = tags.get(4);
        assertEquals("foo", tagFoo.getTagName());
        assertEquals("bar", tagFoo.getTagValue());

    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:tags")
                    .to("microprofile-metrics:counter:test-counter?tags=foo=bar,cheese=wine");

                from("direct:tagsFromHeader")
                    .setHeader(HEADER_METRIC_TAGS, constant("a=b,c=d"))
                    .to("microprofile-metrics:counter:test-counter-header");
            }
        };
    }
}
