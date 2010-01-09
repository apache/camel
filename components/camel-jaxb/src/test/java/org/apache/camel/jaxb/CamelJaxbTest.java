/**
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
package org.apache.camel.jaxb;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.xml.bind.JAXBElement;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.foo.bar.PersonType;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;


public class CamelJaxbTest extends CamelTestSupport {
    
    @Test
    public void testConvertor() throws Exception {
        TypeConverter converter = context.getTypeConverter();
        PersonType person = converter.convertTo(PersonType.class, 
            "<Person><firstName>FOO</firstName><lastName>BAR</lastName></Person>");
        assertNotNull("Person should not be null ", person);
        assertEquals("Get the wrong first name ", person.getFirstName(), "FOO");
        assertEquals("Get the wrong second name ", person.getLastName(), "BAR");
    }
    
    @Test
    public void testFilteringConvertorUnmarshal() throws Exception {
        final byte[] buffers = "<Person><firstName>FOO</firstName><lastName>BAR\u0008</lastName></Person>".getBytes("UTF-8");
        InputStream is = new ByteArrayInputStream(buffers);
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
        TypeConverter converter = context.getTypeConverter();
        PersonType person = converter.convertTo(PersonType.class, exchange, is);
        assertNotNull("Person should not be null ", person);
        assertEquals("Get the wrong first name ", person.getFirstName(), "FOO");
        assertEquals("Get the wrong second name ", person.getLastName(), "BAR ");
    }

    @Test
    public void testUnmarshalBadCharsWithFiltering() throws Exception {
        String xml = "<Person><firstName>FOO</firstName><lastName>BAR\u0008</lastName></Person>";

        PersonType expected = new PersonType();
        expected.setFirstName("FOO");
        expected.setLastName("BAR ");
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedBodiesReceived(expected);

        template.sendBody("direct:unmarshalFilteringEnabled", xml);
        resultEndpoint.assertIsSatisfied();
    }

    @Test(expected = CamelExecutionException.class)
    public void testUnmarshalBadCharsNoFiltering() throws Exception {
        String xml = "<Person><firstName>FOO</firstName><lastName>BAR\u0008</lastName></Person>";
        template.sendBody("direct:getJAXBElementValue", xml);
    }

    @Test
    public void testMarshalBadCharsWithFiltering() throws Exception {
        PersonType person = new PersonType();
        person.setFirstName("foo\u0004");
        person.setLastName("bar");

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        template.sendBody("direct:marshalFilteringEnabled", person);
        resultEndpoint.assertIsSatisfied();

        String body = resultEndpoint.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertFalse("Non-xml character wasn't replaced", body.contains("\u0004"));
    }

    @Test
    public void testMarshalBadCharsNoFiltering() throws Exception {
        PersonType person = new PersonType();
        person.setFirstName("foo\u0004");
        person.setLastName("bar");

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        template.sendBody("direct:marshal", person);
        resultEndpoint.assertIsSatisfied();

        String body = resultEndpoint.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue("Non-xml character unexpectedly did not get into marshalled contents", body
                .contains("\u0004"));
    }

    @Test
    public void testUnmarshal() throws Exception {
        final String xml = "<Person><firstName>FOO</firstName><lastName>BAR</lastName></Person>";
        PersonType expected = new PersonType();
        expected.setFirstName("FOO");
        expected.setLastName("BAR");
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedBodiesReceived(expected);
        resultEndpoint.expectedHeaderReceived("foo", "bar");
        template.sendBodyAndHeader("direct:getJAXBElementValue", xml, "foo", "bar");

        resultEndpoint.assertIsSatisfied();
        resultEndpoint.reset();
        resultEndpoint.expectedMessageCount(1);        
        template.sendBody("direct:getJAXBElement", xml);        
        resultEndpoint.assertIsSatisfied();
        assertTrue("We should get the JAXBElement here", resultEndpoint.getExchanges().get(0).getIn().getBody() instanceof JAXBElement);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            public void configure() throws Exception {
                JaxbDataFormat dataFormat = new JaxbDataFormat("org.apache.camel.foo.bar");
                dataFormat.setIgnoreJAXBElement(false);

                JaxbDataFormat filterEnabledFormat = new JaxbDataFormat("org.apache.camel.foo.bar");
                filterEnabledFormat.setFilterNonXmlChars(true);

                from("direct:getJAXBElementValue")
                    .unmarshal(new JaxbDataFormat("org.apache.camel.foo.bar"))                        
                        .to("mock:result");
                
                from("direct:getJAXBElement")
                    .unmarshal(dataFormat)
                    .to("mock:result");

                from("direct:unmarshalFilteringEnabled")
                    .unmarshal(filterEnabledFormat)
                    .to("mock:result");

                from("direct:marshal")
                    .marshal(dataFormat)
                    .to("mock:result");

                from("direct:marshalFilteringEnabled")
                    .marshal(filterEnabledFormat)
                    .to("mock:result");

            }
        };
    }
    
}
