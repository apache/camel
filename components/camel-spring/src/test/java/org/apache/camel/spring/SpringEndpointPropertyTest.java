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
package org.apache.camel.spring;

import org.apache.camel.component.seda.SedaEndpoint;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringEndpointPropertyTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        System.clearProperty("CamelSedaPollTimeout");

        return new ClassPathXmlApplicationContext("org/apache/camel/spring/SpringEndpointPropertyTest.xml");
    }

    @Test
    public void testEndpointProperty() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        template.sendBody("ref:foo", "Hello World");
        template.sendBody("ref:bar", "Bye World");
        assertMockEndpointsSatisfied();

        SedaEndpoint foo = applicationContext.getBean("foo", SedaEndpoint.class);
        assertNotNull(foo);
        assertEquals(100, foo.getSize());
        assertEquals(250, foo.getPollTimeout());
        assertEquals(true, foo.isBlockWhenFull());
        assertEquals("seda://foo?blockWhenFull=true&pollTimeout=250&size=100", foo.getEndpointUri());

        SedaEndpoint bar = applicationContext.getBean("bar", SedaEndpoint.class);
        assertNotNull(bar);
        assertEquals(200, bar.getSize());
        assertEquals("seda://bar?size=200", bar.getEndpointUri());
    }

}
