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
package org.apache.camel.language.xpath;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class XPathHeaderTest extends CamelSpringTestSupport {

    @Test
    public void testChoiceWithHeaderSelectCamel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:camel");
        mock.expectedBodiesReceived("<name>King</name>");
        mock.expectedHeaderReceived("type", "Camel");

        template.sendBodyAndHeader("direct:in", "<name>King</name>", "type", "Camel");

        mock.assertIsSatisfied();
    }

    @Test
    public void testChoiceWithNoHeaderSelectDonkey() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:donkey");
        mock.expectedBodiesReceived("<name>Kong</name>");

        template.sendBody("direct:in", "<name>Kong</name>");

        mock.assertIsSatisfied();
    }

    @Test
    public void testChoiceWithNoHeaderSelectOther() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:other");
        mock.expectedBodiesReceived("<name>Other</name>");

        template.sendBody("direct:in", "<name>Other</name>");

        mock.assertIsSatisfied();
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/language/xpath/XPathHeaderTest.xml");
    }

}
