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
package org.apache.camel.spring.issues;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision$
 */
public class SpringChristianDLCLoseMessageIssueTest extends SpringTestSupport {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/issues/SpringChristianDLCLoseMessageIssueTest.xml");
    }

    public void testDLCThrowException() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(2);

        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMessageCount(2);

        // should newer get a message as DLC handles it
        MockEndpoint kaboom = getMockEndpoint("mock:kaboom");
        kaboom.expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Kaboom");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Kaboom");

        assertMockEndpointsSatisfied();
    }

    public void testDLCQueueFull() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(2);

        // should get 1 message when seda:bye is full
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

}