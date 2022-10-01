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
package org.apache.camel.component.jacksonxml;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JacksonEmptyElementsAsNullTest extends CamelTestSupport {

    @Test
    public void testEmptyAsNull() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(4);

        template.sendBody("direct:start", "<pojo><name>Jack</name></pojo>");
        template.sendBody("direct:start", "<pojo><name></name></pojo>");
        template.sendBody("direct:start", "<pojo><name/></pojo>");
        template.sendBody("direct:start", "<pojo></pojo>");

        MockEndpoint.assertIsSatisfied(context);

        Assertions.assertEquals("Jack", mock.getReceivedExchanges().get(0).getMessage().getBody(TestPojo.class).getName());
        // <name></name> and <name/> are NOT the same as empty string vs null
        Assertions.assertEquals("", mock.getReceivedExchanges().get(1).getMessage().getBody(TestPojo.class).getName());
        Assertions.assertNull(mock.getReceivedExchanges().get(2).getMessage().getBody(TestPojo.class).getName());
        Assertions.assertNull(mock.getReceivedExchanges().get(3).getMessage().getBody(TestPojo.class).getName());
    }

    @Test
    public void testDefault() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result2");
        mock.expectedMessageCount(4);

        template.sendBody("direct:start2", "<pojo><name>Jack</name></pojo>");
        template.sendBody("direct:start2", "<pojo><name></name></pojo>");
        template.sendBody("direct:start2", "<pojo><name/></pojo>");
        template.sendBody("direct:start2", "<pojo></pojo>");

        MockEndpoint.assertIsSatisfied(context);

        Assertions.assertEquals("Jack", mock.getReceivedExchanges().get(0).getMessage().getBody(TestPojo.class).getName());
        // <name></name> and <name/> are both the same as an empty string
        Assertions.assertEquals("", mock.getReceivedExchanges().get(1).getMessage().getBody(TestPojo.class).getName());
        Assertions.assertEquals("", mock.getReceivedExchanges().get(2).getMessage().getBody(TestPojo.class).getName());
        Assertions.assertNull(mock.getReceivedExchanges().get(3).getMessage().getBody(TestPojo.class).getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                context.setStreamCaching(false);

                JacksonXMLDataFormat format = new JacksonXMLDataFormat(TestPojo.class);
                format.enableFeature(FromXmlParser.Feature.EMPTY_ELEMENT_AS_NULL);
                format.disableFeature(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                from("direct:start").unmarshal(format).to("mock:result");

                JacksonXMLDataFormat format2 = new JacksonXMLDataFormat(TestPojo.class);
                format.disableFeature(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                from("direct:start2").unmarshal(format2).to("mock:result2");
            }
        };
    }

}
