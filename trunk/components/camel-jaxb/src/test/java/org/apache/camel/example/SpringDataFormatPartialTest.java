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
package org.apache.camel.example;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Partial operations test.
 */
public class SpringDataFormatPartialTest extends CamelSpringTestSupport {

    @Test
    public void testPartialMarshal() throws Exception {
        PurchaseOrder bean = new PurchaseOrder();
        bean.setName("Beer");
        bean.setAmount(23);
        bean.setPrice(2.5);

        MockEndpoint mock = resolveMandatoryEndpoint("mock:marshal", MockEndpoint.class);
        mock.expectedMessageCount(1);

        XPathExpression xpath = new XPathExpression("count(//*[namespace-uri() = 'http://example.camel.org/apache' and local-name() = 'po']) = 1");
        xpath.setResultType(Boolean.class);
        mock.allMessages().body().matches(xpath);
        
        template.sendBody("direct:marshal", bean);        
        mock.assertIsSatisfied();
        
        //To make sure there is no XML declaration.
        assertFalse("There should have no XML declaration.", 
                    mock.getExchanges().get(0).getIn().getBody(String.class).startsWith("<?xml version="));
    }

    @Test
    public void testPartialUnmarshal() throws Exception {
        MockEndpoint mock = resolveMandatoryEndpoint("mock:unmarshal", MockEndpoint.class);
        mock.expectedMessageCount(1);

        Partial partial = new Partial();
        partial.setName("mock");
        partial.setLocation("org.apache.camel");
        mock.expectedBodyReceived().constant(partial);

        String xml = "<Partial name=\"mock\"><location>org.apache.camel</location></Partial>";
        template.sendBody("direct:unmarshal", xml);

        mock.assertIsSatisfied();
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/example/springDataFormatPartial.xml");
    }

}