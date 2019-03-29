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
package org.apache.camel.component.jetty;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 */
public class JettyComponentSpringConfiguredTest extends CamelSpringTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jetty/JettyComponentSpringConfiguredTest.xml");
    }

    @Test
    @Ignore("run manual test")
    public void testJetty2() throws Exception {
        assertNotNull("Should have jetty2 component", context.hasComponent("jetty2"));

        String reply = template.requestBody("jetty2:http://localhost:9090/myapp", "Camel", String.class);
        assertEquals("Hello Camel", reply);
    }
}
