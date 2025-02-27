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
package org.apache.camel.component.bean;

import org.apache.camel.spring.SpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class BeanRouteTest extends SpringTestSupport {
    protected Object body = "James";

    @Test
    public void testSayHello() throws Exception {
        Object value = template.requestBody("bean:myBean?method=sayHello", body);

        assertEquals("Hello James!", value, "Returned value");
    }

    @Test
    public void testSayGoodbye() throws Exception {
        Object value = template.requestBody("bean:myBean?method=sayGoodbye", body);

        assertEquals("Bye James!", value, "Returned value");
    }

    @Test
    public void testChooseMethodUsingBodyType() throws Exception {
        Object value = template.requestBody("bean:myBean", 4);

        assertEquals(8L, value, "Returned value");
    }

    @Test
    public void testAmbiguousMethodCallFails() throws Exception {
        try {
            Object value = template.requestBody("bean:myBean", body);
            fail("We should have failed to invoke an ambiguous method but instead got: " + value);
        } catch (Exception e) {
            log.info("Caught expected failure: {}", e.getMessage(), e);
        }
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/bean/camelContext.xml");
    }

}
