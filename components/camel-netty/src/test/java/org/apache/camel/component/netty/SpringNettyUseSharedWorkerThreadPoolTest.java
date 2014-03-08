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
package org.apache.camel.component.netty;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class SpringNettyUseSharedWorkerThreadPoolTest extends CamelSpringTestSupport {

    @Test
    public void testSharedThreadPool() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(30);

        for (int i = 0; i < 10; i++) {
            String reply = template.requestBody("netty:tcp://localhost:5021?textline=true&sync=true", "Hello World", String.class);
            assertEquals("Hello World", reply);

            reply = template.requestBody("netty:tcp://localhost:5022?textline=true&sync=true", "Hello Camel", String.class);
            assertEquals("Hello Camel", reply);

            reply = template.requestBody("netty:tcp://localhost:5023?textline=true&sync=true", "Hello Claus", String.class);
            assertEquals("Hello Claus", reply);
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/netty/SpringNettyUseSharedWorkerThreadPoolTest.xml");
    }
}