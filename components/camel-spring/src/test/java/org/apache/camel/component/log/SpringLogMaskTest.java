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
package org.apache.camel.component.log;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.MaskingFormatter;
import org.apache.camel.spring.SpringCamelContext;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringLogMaskTest {

    @Test
    public void testLogMask() throws Exception {
        final AbstractXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/component/log/SpringLogMaskTest-context.xml");
        SpringCamelContext context = SpringCamelContext.springCamelContext(applicationContext, true);
        context.start();
        MockEndpoint mock = context.getEndpoint("mock:mask", MockEndpoint.class);
        ProducerTemplate template = context.createProducerTemplate();
        template.sendBodyAndHeader("direct:mask", "password=passw0rd@", "headerPassword", "#header-password$");
        template.sendBodyAndProperty("direct:mask", "password=passw0rd@", "propertyPassphrase", "#property-passphrase$");
        context.stop();
        mock.expectedMessageCount(2);
    }

    @Test
    public void testLogMaskDisabled() throws Exception {
        final AbstractXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/component/log/SpringLogMaskTest-context.xml");
        SpringCamelContext context = SpringCamelContext.springCamelContext(applicationContext, true);
        context.start();
        MockEndpoint mock = context.getEndpoint("mock:no-mask", MockEndpoint.class);
        ProducerTemplate template = context.createProducerTemplate();
        template.sendBodyAndHeader("direct:no-mask", "password=passw0rd@", "headerPassword", "#header-password$");
        template.sendBodyAndProperty("direct:no-mask", "password=passw0rd@", "propertyPassphrase", "#property-passphrase$");
        context.stop();
        mock.expectedMessageCount(2);
    }

    @Test
    public void testCustomLogMask() throws Exception {
        final AbstractXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/component/log/SpringCustomLogMaskTest-context.xml");
        SpringCamelContext context = SpringCamelContext.springCamelContext(applicationContext, true);
        MockMaskingFormatter customFormatter = applicationContext.getBean(MaskingFormatter.CUSTOM_LOG_MASK_REF, MockMaskingFormatter.class);
        context.start();
        ProducerTemplate template = context.createProducerTemplate();
        template.sendBodyAndHeader("direct:mock", "password=passw0rd@", "headerPassword", "#header-password$");
        context.stop();
        Assert.assertTrue(customFormatter.received.contains("password=passw0rd@"));
    }

    public static class MockMaskingFormatter implements MaskingFormatter {
        private String received;
        @Override
        public String format(String source) {
            received = source;
            return source;
        }
    }


}