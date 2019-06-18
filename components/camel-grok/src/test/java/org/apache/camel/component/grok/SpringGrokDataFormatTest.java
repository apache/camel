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

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringGrokDataFormatTest extends CamelSpringTestSupport {

    @Test
    public void testMultipleIpCustomRef() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:ipCustom");
        template.sendBody("direct:ipCustom", "178.21.82.201 178.21.82.202\n178.21.82.203\r\n178.21.82.204");

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = result.getExchanges().get(0).getIn().getBody(List.class);

        Assert.assertEquals(4, body.size());

        Assert.assertEquals("178.21.82.201", body.get(0).get("ip"));
        Assert.assertEquals("178.21.82.202", body.get(1).get("ip"));
        Assert.assertEquals("178.21.82.203", body.get(2).get("ip"));
        Assert.assertEquals("178.21.82.204", body.get(3).get("ip"));
    }

    @Test
    public void testMultipleIp() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:ip");
        template.sendBody("direct:ip", "178.21.82.201 178.21.82.202\n178.21.82.203\r\n178.21.82.204");

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = result.getExchanges().get(0).getIn().getBody(List.class);

        Assert.assertEquals(4, body.size());

        Assert.assertEquals("178.21.82.201", body.get(0).get("ip"));
        Assert.assertEquals("178.21.82.202", body.get(1).get("ip"));
        Assert.assertEquals("178.21.82.203", body.get(2).get("ip"));
        Assert.assertEquals("178.21.82.204", body.get(3).get("ip"));
    }

    @Test
    public void testCustomPattern() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:fooBar");
        template.sendBody("direct:fooBar", "bar foobar bar -- barbarfoobarfoobar -- barbar");

        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        Assert.assertEquals(
                "-- barbarfoobarfoobar --",
                result.getExchanges().get(0).getIn().getBody(Map.class).get("fooBar"));
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/grok/SpringGrokDataFormatTest.xml");
    }

}
