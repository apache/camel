/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring;

import org.apache.camel.TestSupport;
import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.CamelClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision: 521586 $
 */
public class RoutingUsingCamelContextFactoryTest extends TestSupport {
    protected String body = "<hello>world!</hello>";

    public void testXMLRouteLoading() throws Exception {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/spring/routingUsingCamelContextFactoryTest.xml");

        CamelContext context = (CamelContext) applicationContext.getBean("camel");
        assertNotNull("No context found!", context);

        MockEndpoint resultEndpoint = (MockEndpoint) resolveMandatoryEndpoint(context, "mock:result");
        resultEndpoint.expectedBodiesReceived(body);

        // now lets send a message
        CamelClient<Exchange> client = new CamelClient<Exchange>(context);
        client.send("queue:start", new Processor<Exchange>() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setHeader("name", "James");
                in.setBody(body);
            }
        });
        

        resultEndpoint.assertIsSatisfied();
    }

}
