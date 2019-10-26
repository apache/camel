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
package org.apache.camel.builder.xml;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * XPath with and without header test.
 */
public class XPathFunctionsTest extends ContextTestSupport {

    @Test
    public void testChoiceWithHeaderAndPropertiesSelectCamel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:camel");
        mock.expectedBodiesReceived("<name>King</name>");
        mock.expectedHeaderReceived("type", "Camel");

        template.sendBodyAndHeader("direct:in", "<name>King</name>", "type", "Camel");

        mock.assertIsSatisfied();
    }

    @Test
    public void testChoiceWithNoHeaderAndPropertiesSelectDonkey() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:donkey");
        mock.expectedBodiesReceived("<name>Donkey Kong</name>");

        template.sendBody("direct:in", "<name>Donkey Kong</name>");

        mock.assertIsSatisfied();
    }

    @Test
    public void testChoiceWithNoHeaderAndPropertiesSelectOther() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:other");
        mock.expectedBodiesReceived("<name>Other</name>");

        template.sendBody("direct:in", "<name>Other</name>");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: ex
                // setup properties component
                context.getPropertiesComponent().setLocation("classpath:org/apache/camel/builder/xml/myprop.properties");

                // myprop.properties contains the following properties
                // foo=Camel
                // bar=Kong

                from("direct:in").choice()
                    // $type is a variable for the header with key type
                    // here we use the properties function to lookup foo from
                    // the properties files
                    // which at runtime will be evaluted to 'Camel'
                    .when().xpath("$type = function:properties('foo')").to("mock:camel")
                    // here we use the simple language to evaluate the
                    // expression
                    // which at runtime will be evaluated to 'Donkey Kong'
                    .when().xpath("//name = function:simple('Donkey {{bar}}')").to("mock:donkey").otherwise().to("mock:other").end();
                // END SNIPPET: ex
            }
        };
    }
}
