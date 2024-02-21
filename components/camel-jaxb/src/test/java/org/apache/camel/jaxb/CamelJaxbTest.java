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
package org.apache.camel.jaxb;

import jakarta.xml.bind.JAXBElement;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.foo.bar.PersonType;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelJaxbTest extends CamelTestSupport {

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

    @Test
    public void testUnmarshalBadCharsNoFiltering() {
        String xml = "<Person><firstName>FOO</firstName><lastName>BAR\u0008</lastName></Person>";
        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:getJAXBElementValue", xml));
    }

    @Test
    public void testFilterNonXmlChars() throws Exception {
        String xmlUTF = "<Person><firstName>FOO</firstName><lastName>BAR \u20AC </lastName></Person>";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + xmlUTF;
        PersonType expected = new PersonType();
        expected.setFirstName("FOO");
        expected.setLastName("BAR \u20AC ");
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedBodiesReceived(expected);
        template.sendBody("direct:unmarshalFilteringEnabled", xml);
        resultEndpoint.assertIsSatisfied();

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
        assertFalse(body.contains("\u0004"), "Non-xml character wasn't replaced");
    }

    @Test
    public void testMarshalBadCharsNoFiltering() throws Exception {
        PersonType person = new PersonType();
        person.setFirstName("foo\u0004");
        person.setLastName("bar");

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedHeaderReceived(Exchange.CONTENT_TYPE, "application/xml");
        template.sendBody("direct:marshal", person);
        resultEndpoint.assertIsSatisfied();

        String body = resultEndpoint.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue(body.contains("\u0004"), "Non-xml character unexpectedly did not get into marshalled contents");
    }

    @Test
    public void testMarshalWithSchemaLocation() throws Exception {
        PersonType person = new PersonType();
        person.setFirstName("foo");
        person.setLastName("bar");

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedHeaderReceived(Exchange.CONTENT_TYPE, "application/xml");
        template.sendBody("direct:marshal", person);
        resultEndpoint.assertIsSatisfied();

        String body = resultEndpoint.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue(body.contains("schemaLocation=\"person.xsd\""), "We should get the schemaLocation here");
    }

    @Test
    public void testMarshalWithoutContentType() throws Exception {
        PersonType person = new PersonType();
        person.setFirstName("foo");
        person.setLastName("bar");

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedHeaderReceived(Exchange.CONTENT_TYPE, null);
        template.sendBody("direct:marshalWithoutContentType", person);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testCustomXmlStreamWriter() throws InterruptedException {
        PersonType person = new PersonType();
        person.setFirstName("foo");
        person.setLastName("bar");

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        template.sendBody("direct:marshalCustomWriter", person);
        resultEndpoint.assertIsSatisfied();

        String body = resultEndpoint.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue(body.contains("-Foo"), "Body did not get processed correctly by custom filter");
    }

    @Test
    public void testCustomXmlStreamWriterAndFiltering() throws InterruptedException {
        PersonType person = new PersonType();
        person.setFirstName("foo\u0004");
        person.setLastName("bar");

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        template.sendBody("direct:marshalCustomWriterAndFiltering", person);
        resultEndpoint.assertIsSatisfied();

        String body = resultEndpoint.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertFalse(body.contains("\u0004"), "Non-xml character unexpectedly did not get into marshalled contents");
        assertTrue(body.contains("-Foo"), "Body did not get processed correctly by custom filter");
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
        assertTrue(resultEndpoint.getExchanges().get(0).getIn().getBody() instanceof JAXBElement,
                "We should get the JAXBElement here");

        resultEndpoint.reset();
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedBodiesReceived(expected);
        template.sendBody("direct:unmarshall", xml);
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                JaxbDataFormat dataFormat = new JaxbDataFormat("org.apache.camel.foo.bar");
                dataFormat.setSchemaLocation("person.xsd");
                dataFormat.setIgnoreJAXBElement(false);
                dataFormat.setAccessExternalSchemaProtocols("file");

                JaxbDataFormat dataFormatWithoutContentType = new JaxbDataFormat("org.apache.camel.foo.bar");
                dataFormat.setIgnoreJAXBElement(false);
                dataFormatWithoutContentType.setContentTypeHeader(false);

                JaxbDataFormat filterEnabledFormat = new JaxbDataFormat("org.apache.camel.foo.bar");
                filterEnabledFormat.setFilterNonXmlChars(true);

                JaxbDataFormat customWriterFormat = new JaxbDataFormat("org.apache.camel.foo.bar");
                customWriterFormat.setXmlStreamWriterWrapper(new TestXmlStreamWriter());

                JaxbDataFormat customWriterAndFilterFormat = new JaxbDataFormat("org.apache.camel.foo.bar");
                customWriterAndFilterFormat.setFilterNonXmlChars(true);
                customWriterAndFilterFormat.setXmlStreamWriterWrapper(new TestXmlStreamWriter());

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

                from("direct:marshalWithoutContentType")
                        .marshal(dataFormatWithoutContentType)
                        .to("mock:result");

                from("direct:marshalFilteringEnabled")
                        .marshal(filterEnabledFormat)
                        .to("mock:result");

                from("direct:marshalCustomWriter")
                        .marshal(customWriterFormat)
                        .to("mock:result");
                from("direct:marshalCustomWriterAndFiltering")
                        .marshal(customWriterAndFilterFormat)
                        .to("mock:result");

                from("direct:unmarshall")
                        .unmarshal()
                        .jaxb(PersonType.class.getPackage().getName())
                        .to("mock:result");

            }
        };
    }

}
