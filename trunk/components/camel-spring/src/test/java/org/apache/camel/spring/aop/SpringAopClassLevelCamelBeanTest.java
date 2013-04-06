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
package org.apache.camel.spring.aop;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Spring AOP will proxy a bean at class level which is also a bean that Camel should invoke
 * using its bean component. The test should test that Camel bean binding annotations works.
 *
 * @version 
 */
public class SpringAopClassLevelCamelBeanTest extends SpringTestSupport {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/aop/SpringAopClassLevelCamelBeanTest.xml");
    }

    public void testSpringAopOk() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedHeaderReceived("foo", 123);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "ABC");

        assertMockEndpointsSatisfied();
    }

    public void testSpringAopException() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.sendBodyAndHeader("direct:start", "Hello World", "foo", "Damn");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        }

        assertMockEndpointsSatisfied();

        ExceptionInterceptor ei = context.getRegistry().lookupByNameAndType("exceptionInterceptor", ExceptionInterceptor.class);
        IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, ei.getE());
        assertEquals("Foo has not expected value ABC but Damn", iae.getMessage());
    }

}
