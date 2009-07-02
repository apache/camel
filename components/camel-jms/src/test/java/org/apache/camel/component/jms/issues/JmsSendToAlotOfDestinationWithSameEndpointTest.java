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
package org.apache.camel.component.jms.issues;

import org.apache.camel.component.jms.JmsConstants;
import org.apache.camel.spring.SpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;

/**
 * @version $Revision$
 */
public class JmsSendToAlotOfDestinationWithSameEndpointTest extends SpringTestSupport {

    private static String URI = "activemq:queue:foo";

    public int getExpectedRouteCount() {
        return 0;
    }

    @Test
    public void testSendToAlotOfMessageToQueues() throws Exception {
        int size = 100;

        for (int i = 0; i < size; i++) {
            // use the same endpoint but provide a header with the dynamic queue we send to
            // this allows us to reuse endpoints and not create a new endpoint for each and every jms queue
            // we send to
            template.sendBodyAndHeader(URI, "Hello " + i, JmsConstants.JMS_DESTINATION_NAME, "foo" + i);
        }

        // now we should be able to poll a message from each queue
//        System.out.println(size + " messages sent, use jconsole to look");
//        Thread.sleep(99999999);
    }

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(new String[]{"classpath:org/apache/camel/component/jms/issues/broker.xml",
                "classpath:org/apache/camel/component/jms/issues/camelBrokerClient.xml"});
    }

}
