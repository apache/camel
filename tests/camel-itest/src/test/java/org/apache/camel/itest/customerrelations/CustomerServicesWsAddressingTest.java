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
package org.apache.camel.itest.customerrelations;

import org.apache.camel.util.IOHelper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CustomerServicesWsAddressingTest extends Assert {

    @Test
    public void testCustomerService() throws Exception {
        ClassPathXmlApplicationContext serverContext = null;
        ClassPathXmlApplicationContext clientContext = null;
        try {
            serverContext = new ClassPathXmlApplicationContext(
                new String[] {"spring-config/server-WsAddressingContext.xml"});
            Object server = serverContext.getBean("org.apache.camel.itest.customerrelations.CustomerServiceV1");
            assertNotNull("We should get server here", server);

            clientContext =  new ClassPathXmlApplicationContext(
                new String[] {"spring-config/client-WsAddressingContext.xml"});
            CustomerServiceV1 customerService = clientContext.getBean("org.apache.camel.itest.customerrelations.CustomerServiceV1", CustomerServiceV1.class);

            Customer customer = customerService.getCustomer("12345");
            assertNotNull("We should get Customer here", customer);
        } finally {
            // we're done so let's properly close the application context
            IOHelper.close(clientContext, serverContext);
        }
    }

}
