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
package org.apache.camel.component.krati;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class KratiProducerSpringTest extends CamelSpringTestSupport {

    @Test
    public void testPut() throws Exception {
        MockEndpoint endpoint = context.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedMessageCount(3);

        template.sendBodyAndHeader("direct:put", "TEST1", KratiConstants.KEY, "1");
        template.sendBodyAndHeader("direct:put", "TEST2", KratiConstants.KEY, "2");
        template.sendBodyAndHeader("direct:put", "TEST3", KratiConstants.KEY, "3");

        endpoint.assertIsSatisfied();
    }

    @Test
    public void testPutAndGet() throws Exception {
        MockEndpoint endpoint = context.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedMessageCount(3);

        template.sendBodyAndHeader("direct:put", "TEST1", KratiConstants.KEY, "1");
        template.sendBodyAndHeader("direct:put", "TEST2", KratiConstants.KEY, "2");
        template.sendBodyAndHeader("direct:put", "TEST3", KratiConstants.KEY, "3");

        endpoint.assertIsSatisfied();

        Object result = template.requestBodyAndHeader("direct:get", null, KratiConstants.KEY, "3");
        assertEquals("TEST3", result);
    }

    @Test
    public void testPutDeleteAndGet() throws Exception {
        template.sendBodyAndHeader("direct:put", "TEST1", KratiConstants.KEY, "1");
        template.sendBodyAndHeader("direct:put", "TEST2", KratiConstants.KEY, "2");
        template.sendBodyAndHeader("direct:put", "TEST3", KratiConstants.KEY, "3");
        template.requestBodyAndHeader("direct:delete", null, KratiConstants.KEY, "3");

        Object result = template.requestBodyAndHeader("direct:get", null, KratiConstants.KEY, "3");
        assertEquals(null, result);
    }

    @Test
    public void testPutDeleteAllAndGet() throws Exception {
        template.sendBodyAndHeader("direct:put", "TEST1", KratiConstants.KEY, "1");
        template.sendBodyAndHeader("direct:put", "TEST2", KratiConstants.KEY, "2");
        template.sendBodyAndHeader("direct:put", "TEST3", KratiConstants.KEY, "3");
        template.requestBodyAndHeader("direct:deleteall", null, KratiConstants.KEY, "3");

        Object result = template.requestBodyAndHeader("direct:get", null, KratiConstants.KEY, "1");
        assertEquals(null, result);
        result = template.requestBodyAndHeader("direct:get", null, KratiConstants.KEY, "2");
        assertEquals(null, result);
        result = template.requestBodyAndHeader("direct:get", null, KratiConstants.KEY, "3");
        assertEquals(null, result);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("classpath:producer-test.xml");
    }
}
