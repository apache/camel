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
package org.apache.camel.component.grok;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class GrokUnmarshalTest extends CamelTestSupport {
    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindToRegistry("myCustomPatternBean", new GrokPattern("FOOBAR", "foo|bar"));
                bindToRegistry("myAnotherCustomPatternBean",
                        new GrokPattern("FOOBAR_WITH_PREFIX_AND_SUFFIX", "-- %{FOOBAR}+ --"));

                from("direct:ip")
                        .unmarshal().grok("%{IP:ip}")
                        .to("mock:ip");

                from("direct:fooBar")
                        .unmarshal().grok("%{FOOBAR_WITH_PREFIX_AND_SUFFIX:fooBar}")
                        .to("mock:fooBar");
            }
        };
    }

    @Test
    public void testSingleIp() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:ip");
        template.sendBody("direct:ip", "178.21.82.201");
        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        Assert.assertEquals("178.21.82.201", result.getExchanges().get(0).getIn().getBody(Map.class).get("ip"));
    }

    @Test
    public void testMultipleIpSingleLine() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:ip");
        template.sendBody("direct:ip", "178.21.82.201 178.21.82.202 178.21.82.203 178.21.82.204");

        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        Assert.assertEquals("178.21.82.201", get(result, 0, 0, "ip"));
        Assert.assertEquals("178.21.82.202", get(result, 0, 1, "ip"));
        Assert.assertEquals("178.21.82.203", get(result, 0, 2, "ip"));
        Assert.assertEquals("178.21.82.204", get(result, 0, 3, "ip"));
    }

    @Test
    public void testMultipleIpMultipleLineMixedLineEndings() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:ip");
        template.sendBody("direct:ip", "178.21.82.201 178.21.82.202\n178.21.82.203\r\n178.21.82.204");

        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        Assert.assertEquals("178.21.82.201", get(result, 0, 0, "ip"));
        Assert.assertEquals("178.21.82.202", get(result, 0, 1, "ip"));
        Assert.assertEquals("178.21.82.203", get(result, 0, 2, "ip"));
        Assert.assertEquals("178.21.82.204", get(result, 0, 3, "ip"));
    }

    @Test
    public void testCustomPattern() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:fooBar");
        template.sendBody("direct:fooBar", "bar foobar bar -- barbarfoobarfoobar -- barbar");

        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        Assert.assertEquals(
                "-- barbarfoobarfoobar --",
                result.getExchanges().get(0).getIn().getBody(Map.class).get("fooBar")
        );
    }

    private Object get(Exchange exchange, int listIndex, String mapKey) {
        Assert.assertNotNull("Body should not be null", exchange.getIn().getBody(List.class));
        List list = exchange.getIn().getBody(List.class);
        Assert.assertTrue(list.size() > listIndex);
        Assert.assertTrue(list.get(listIndex) instanceof Map);
        return ((Map) list.get(listIndex)).get(mapKey);
    }

    private Object get(MockEndpoint mockEndpoint, int exchangeIndex, int listIndex, String mapKey) {
        Assert.assertTrue(mockEndpoint.getExchanges().size() > exchangeIndex);
        return get(mockEndpoint.getExchanges().get(exchangeIndex), listIndex, mapKey);
    }
}
