/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring;

import org.apache.camel.Endpoint;
import org.apache.camel.spring.example.DummyBean;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision: 521586 $
 */
public class EndpointXmlConfigTest extends SpringTestSupport {
    public void testEndpointConfiguration() throws Exception {
        Endpoint endpoint = getMandatoryBean(Endpoint.class, "endpoint1");

        assertEquals("endpoint URI", "mock:endpoint1", endpoint.getEndpointUri());

        DummyBean dummyBean = getMandatoryBean(DummyBean.class, "mybean");
        assertNotNull("The bean should have an endpoint injected", dummyBean.getEndpoint());
        assertEquals("endpoint URI", "mock:endpoint1", dummyBean.getEndpoint().getEndpointUri());

        log.debug("Found dummy bean: " + dummyBean);
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/endpointReference.xml");
    }
}
