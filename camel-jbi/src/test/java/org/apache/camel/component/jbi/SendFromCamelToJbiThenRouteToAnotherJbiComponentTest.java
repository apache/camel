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
package org.apache.camel.component.jbi;

import org.apache.camel.builder.RouteBuilder;
import org.apache.servicemix.jbi.container.ActivationSpec;
import org.apache.servicemix.tck.MessageList;
import org.apache.servicemix.tck.ReceiverComponent;

import javax.jbi.messaging.NormalizedMessage;
import javax.xml.namespace.QName;
import java.util.List;

/**
 * @version $Revision$
 */
public class SendFromCamelToJbiThenRouteToAnotherJbiComponentTest extends JbiTestSupport {
    private ReceiverComponent receiverComponent = new ReceiverComponent();

    public void testCamelInvokingJbi() throws Exception {
        sendExchange("<foo bar='123'/>");
        MessageList list = receiverComponent.getMessageList();

        list.assertMessagesReceived(1);
        List messages = list.getMessages();
        NormalizedMessage message = (NormalizedMessage) messages.get(0);
        assertNotNull("null message!", message);
        log.info("Received: " + message);

        assertEquals("cheese header", 123, message.getProperty("cheese"));
    }

    protected RouteBuilder createRoutes() {
        return new RouteBuilder() {
            public void configure() {
                from("jbi:endpoint:serviceNamespace:serviceA:endpointA").to("jbi:endpoint:serviceNamespace:serviceB:endpointB");
            }
        };
    }

    protected void appendJbiActivationSpecs(List<ActivationSpec> activationSpecList) {
        ActivationSpec activationSpec = new ActivationSpec();
        activationSpec.setId("jbiReceiver");
        activationSpec.setService(new QName("serviceNamespace", "serviceB"));
        activationSpec.setEndpoint("endpointB");
        activationSpec.setComponent(receiverComponent);

        activationSpecList.add(activationSpec);
    }

    @Override
    protected void tearDown() throws Exception {
        camelContext.stop();
    }
}
