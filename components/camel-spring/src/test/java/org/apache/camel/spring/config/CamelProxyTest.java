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
package org.apache.camel.spring.config;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.TestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision$
 */
public class CamelProxyTest extends TestCase {

    public void testCamelProxy() throws Exception {
        ApplicationContext ac = new ClassPathXmlApplicationContext("org/apache/camel/spring/config/CamelProxyTest.xml");

        MyProxySender sender = (MyProxySender) ac.getBean("myProxySender");
        String reply = sender.hello("World");

        assertEquals("Hello World", reply);
        
        // test sending inOnly message
        MyProxySender anotherSender = (MyProxySender) ac.getBean("myAnotherProxySender");
        CamelContext context = (CamelContext) ac.getBean("myCamel");
        MockEndpoint result = TestSupport.resolveMandatoryEndpoint(context, "mock:result", MockEndpoint.class);
        result.expectedBodiesReceived("Hello my friends!");
        
        anotherSender.greeting("Hello my friends!");
        result.assertIsSatisfied();
        
        result.reset();
        // test sending inOnly message with other sender
        MyProxySender myProxySenderWithCamelContextId = (MyProxySender) ac.getBean("myProxySenderWithCamelContextId");
        
        result.expectedBodiesReceived("Hello my friends again!");
        myProxySenderWithCamelContextId.greeting("Hello my friends again!");
        result.assertIsSatisfied();
        
    }
    
}