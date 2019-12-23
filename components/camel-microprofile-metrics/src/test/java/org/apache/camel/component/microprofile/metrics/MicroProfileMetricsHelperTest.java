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

import io.smallrye.metrics.MetricRegistries;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MicroProfileMetricsHelperTest {

    @Test
    public void testParseTag() {
        Tag tag = MicroProfileMetricsHelper.parseTag("foo=bar");
        assertEquals("foo", tag.getTagName());
        assertEquals("bar", tag.getTagValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTagForEmptyString() {
        MicroProfileMetricsHelper.parseTag("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTagForInvalidString() {
        MicroProfileMetricsHelper.parseTag("badtag");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTagForStringWithNotEnoughElements() {
        MicroProfileMetricsHelper.parseTag("badtag=");
    }

    @Test
    public void testParseTags() {
        Tag[] tags = MicroProfileMetricsHelper.parseTagArray(new String[] {"foo=bar", "cheese=wine"});
        assertEquals(2, tags.length);
        assertEquals("foo", tags[0].getTagName());
        assertEquals("bar", tags[0].getTagValue());
        assertEquals("cheese", tags[1].getTagName());
        assertEquals("wine", tags[1].getTagValue());
    }

    @Test
    public void testGetMetricRegistry() {
        DefaultCamelContext camelContext = new DefaultCamelContext();
        Registry registry = camelContext.getRegistry();
        registry.bind(MicroProfileMetricsConstants.METRIC_REGISTRY_NAME, MetricRegistries.get(MetricRegistry.Type.APPLICATION));
        MicroProfileMetricsHelper.getMetricRegistry(camelContext);
    }

    @Test(expected = IllegalStateException.class)
    public void testGetMetricRegistryWhenNoRegistryConfigured() {
        MicroProfileMetricsHelper.getMetricRegistry(new DefaultCamelContext());
    }
}
