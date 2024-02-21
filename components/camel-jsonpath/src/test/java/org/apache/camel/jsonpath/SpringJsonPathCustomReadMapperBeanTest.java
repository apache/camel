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
package org.apache.camel.jsonpath;

import java.io.File;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpringJsonPathCustomReadMapperBeanTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/jsonpath/SpringJsonPathCustomReadMapperBeanTest.xml");
    }

    @Test
    public void testJsonPathSplitNumbers() throws Exception {
        // /CAMEL-17956
        MockEndpoint m = getMockEndpoint("mock:result");
        m.expectedMessageCount(1);
        template.requestBody("direct:start", new File("src/test/resources/bignumbers.json"), String.class);
        Object resultFromMock = m.getReceivedExchanges().get(0).getMessage().getBody();
        //        assertTrue(resultFromMock instanceof Map);
        //        Map<String,Object> resultMap = (Map)resultFromMock;
        //        assertTrue(resultMap.get("principalAmountOnValueDate") instanceof BigDecimal);
        assertTrue(resultFromMock instanceof String);
        assertTrue(resultFromMock.toString().contains("121002700.0"));
        assertTrue(resultFromMock.toString().contains("-91000000.0"));
        MockEndpoint.assertIsSatisfied(context);

    }

}
