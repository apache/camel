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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.foo.bar.PersonType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;


public class CamelJaxbNoNamespaceSchemaTest extends CamelTestSupport {
    
    @Test
    public void testMarshalWithNoNamespaceSchemaLocation() throws Exception {
        PersonType person = new PersonType();
        person.setFirstName("foo");
        person.setLastName("bar");

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        template.sendBody("direct:marshal", person);
        resultEndpoint.assertIsSatisfied();

        String body = resultEndpoint.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue("We should get the noNamespaceSchemaLocation here", body
                .contains("noNamespaceSchemaLocation=\"person-no-namespace.xsd\""));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            public void configure() throws Exception {
                JaxbDataFormat dataFormat = new JaxbDataFormat("org.apache.camel.foo.bar");
                dataFormat.setNoNamespaceSchemaLocation("person-no-namespace.xsd");
                dataFormat.setIgnoreJAXBElement(false);


                from("direct:marshal")
                    .marshal(dataFormat)
                    .to("mock:result");

            }
        };
    }
    
}
