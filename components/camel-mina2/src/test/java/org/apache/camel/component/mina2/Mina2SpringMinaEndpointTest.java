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
package org.apache.camel.component.mina2;


import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Unit test spring based mina endpoint configuration.
 */
public class Mina2SpringMinaEndpointTest extends CamelSpringTestSupport {

    @Test
    public void testMinaSpringEndpoint() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        template.sendBody("myMinaEndpoint", "Hello World");

        assertMockEndpointsSatisfied();
        // checking the endpoint uri
        Mina2Endpoint endpoint = applicationContext.getBean("myMinaEndpoint", Mina2Endpoint.class);
        Integer port = applicationContext.getBean("port", Integer.class);
        assertEquals("mina2:tcp:localhost:" + port, endpoint.getEndpointUri());
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/mina2/SpringMinaEndpointTest-context.xml");
    }
}
