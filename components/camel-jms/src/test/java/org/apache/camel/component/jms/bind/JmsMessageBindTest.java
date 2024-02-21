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
package org.apache.camel.component.jms.bind;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.jms.AbstractSpringJMSTestSupport;
import org.apache.camel.component.jms.JmsBinding;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tags({ @Tag("not-parallel"), @Tag("spring"), @Tag("bind") })
public class JmsMessageBindTest extends AbstractSpringJMSTestSupport {

    @Test
    public void testSendAMessageToBean() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedBodiesReceived("Completed");

        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", "bar");
        // this header should not be sent as its value cannot be serialized
        headers.put("binding", new JmsBinding());

        template.sendBodyAndHeaders("activemq:Test.BindingQueue", "SomeBody", headers);

        // lets wait for the method to be invoked
        MockEndpoint.assertIsSatisfied(context);

        // now lets test that the bean is correct
        MyBean bean = getMandatoryBean(MyBean.class, "myBean");
        assertEquals("SomeBody", bean.getBody(), "body");

        Map<?, ?> beanHeaders = bean.getHeaders();
        assertNotNull(beanHeaders, "No headers!");

        assertEquals("bar", beanHeaders.get("foo"), "foo header");
        assertNull(beanHeaders.get("binding"), "Should get a null value");
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/bind/spring.xml");
    }
}
