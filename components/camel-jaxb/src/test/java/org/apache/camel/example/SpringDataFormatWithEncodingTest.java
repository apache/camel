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

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class SpringDataFormatWithEncodingTest extends CamelSpringTestSupport {

    @Test
    public void testMarshalWithEncoding() throws Exception {
        PurchaseOrder bean = new PurchaseOrder();
        bean.setName("Beer");
        bean.setAmount(23);
        bean.setPrice(2.5);

        MockEndpoint mock = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        mock.message(0).body(String.class).startsWith("<?xml version=\"1.0\" encoding=\"iso-8859-1\" standalone=\"yes\"?>");
        mock.message(0).body(String.class).contains("purchaseOrder");
        mock.message(0).body(String.class).contains("amount=\"23.0\"");
        mock.message(0).body(String.class).contains("price=\"2.5\"");
        mock.message(0).body(String.class).contains("name=\"Beer\"");

        template.sendBody("direct:start", bean);

        mock.assertIsSatisfied();
    }

    @Test
    public void testMarshalWithEncodingPropertyInExchange() throws Exception {
        PurchaseOrder bean = new PurchaseOrder();
        bean.setName("Beer");
        bean.setAmount(23);
        bean.setPrice(2.5);

        MockEndpoint mock = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        mock.message(0).body(String.class).startsWith("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>");
        mock.message(0).body(String.class).contains("purchaseOrder");
        mock.message(0).body(String.class).contains("amount=\"23.0\"");
        mock.message(0).body(String.class).contains("price=\"2.5\"");
        mock.message(0).body(String.class).contains("name=\"Beer\"");

        // the property should override the jaxb configuration
        template.sendBodyAndProperty("direct:start", bean, Exchange.CHARSET_NAME, "utf-8");

        mock.assertIsSatisfied();
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/example/springDataFormatWithEncoding.xml");
    }
}