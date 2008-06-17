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

import java.util.Map;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision$
 */
public class JmsMessageBindTest extends SpringTestSupport {
    
    public void testSendAMessageToBean() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedBodiesReceived("Completed");

        template.sendBodyAndHeader("activemq:Test.BindingQueue", "SomeBody", "foo", "bar");

        // lets wait for the method to be invoked
        assertMockEndpointsSatisifed();

        // now lets test that the bean is correct
        MyBean bean = getMandatoryBean(MyBean.class, "myBean");
        assertEquals("body", "SomeBody", bean.getBody());

        Map headers = bean.getHeaders();
        assertNotNull("No headers!", headers);
        assertEquals("foo header", "bar", headers.get("foo"));
    }

    @Override
    protected int getExpectedRouteCount() {
        return 0;
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/bind/spring.xml");
    }
}
