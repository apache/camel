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
package org.apache.camel.component.jms.bind;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.jms.JmsBinding;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class JmsMessageBindTest extends CamelSpringTestSupport {
    
    @Test
    public void testSendAMessageToBean() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedBodiesReceived("Completed");
        
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("foo", "bar");
        // this header should not be sent as its value cannot be serialized 
        headers.put("binding", new JmsBinding());

        template.sendBodyAndHeaders("activemq:Test.BindingQueue", "SomeBody", headers);

        // lets wait for the method to be invoked
        assertMockEndpointsSatisfied();

        // now lets test that the bean is correct
        MyBean bean = getMandatoryBean(MyBean.class, "myBean");
        assertEquals("body", "SomeBody", bean.getBody());

        Map<?, ?> beanHeaders = bean.getHeaders();
        assertNotNull("No headers!", beanHeaders);
        
        assertEquals("foo header", "bar", beanHeaders.get("foo"));
        assertNull("Should get a null value", beanHeaders.get("binding"));
    }


    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/bind/spring.xml");
    }
}
