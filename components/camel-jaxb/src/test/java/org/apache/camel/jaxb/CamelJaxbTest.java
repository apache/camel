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

import javax.xml.bind.JAXBElement;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.foo.bar.PersonType;


public class CamelJaxbTest extends ContextTestSupport {
    
    public void testConvertor() throws Exception {
        TypeConverter converter = context.getTypeConverter();
        PersonType person = converter.convertTo(PersonType.class, 
            "<Person><firstName>FOO</firstName><lastName>BAR</lastName></Person>");
        assertNotNull("Person should not be null ", person);
        assertEquals("Get the wrong first name ", person.getFirstName(), "FOO");
        assertEquals("Get the wrong second name ", person.getLastName(), "BAR");
    }

    public void testUnmarshal() throws Exception {
        final String xml = "<Person><firstName>FOO</firstName><lastName>BAR</lastName></Person>";
        PersonType expected = new PersonType();
        expected.setFirstName("FOO");
        expected.setLastName("BAR");
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedBodiesReceived(expected);
        template.sendBody("direct:getJAXBElementValue", xml);

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
                from("direct:getJAXBElementValue")
                    .unmarshal(new JaxbDataFormat("org.apache.camel.foo.bar"))                        
                        .to("mock:result");
                
                from("direct:getJAXBElement")
                    .unmarshal(dataFormat)
                        .to("mock:result");
            }
        };
    }
    
}
