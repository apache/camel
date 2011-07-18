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
package org.apache.camel.itest.jms;

import javax.naming.Context;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jndi.JndiContext;
import org.junit.Test;

/**
 * @version 
 */
public class JmsValidatorTest extends CamelTestSupport {

    @Test
    public void testJmsValidator() throws Exception {
        String body = "<?xml version=\"1.0\"?>\n<p>Hello world!</p>";

        template.sendBody("jms:queue:inbox", body);

        // wait a sec
        Thread.sleep(1000);

        // it should end up in the valid and finally queue
        BrowsableEndpoint bev = context.getEndpoint("jms:queue:valid", BrowsableEndpoint.class);
        assertEquals(1, bev.getExchanges().size());

        BrowsableEndpoint beiv = context.getEndpoint("jms:queue:invalid", BrowsableEndpoint.class);
        assertEquals(0, beiv.getExchanges().size());

        BrowsableEndpoint bef = context.getEndpoint("jms:queue:finally", BrowsableEndpoint.class);
        assertEquals(1, bef.getExchanges().size());
    }

    @Test
    public void testJmsValidatorInvalid() throws Exception {
        String body = "<?xml version=\"1.0\"?>\n<foo>Kaboom</foo>";

        template.sendBody("jms:queue:inbox", body);

        // wait a sec
        Thread.sleep(1000);

        // it should end up in the invalid and finally queue
        BrowsableEndpoint bev = context.getEndpoint("jms:queue:valid", BrowsableEndpoint.class);
        assertEquals(0, bev.getExchanges().size());

        BrowsableEndpoint beiv = context.getEndpoint("jms:queue:invalid", BrowsableEndpoint.class);
        assertEquals(1, beiv.getExchanges().size());

        BrowsableEndpoint bef = context.getEndpoint("jms:queue:finally", BrowsableEndpoint.class);
        assertEquals(1, bef.getExchanges().size());
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();

        // add ActiveMQ with embedded broker
        ActiveMQComponent amq = ActiveMQComponent.activeMQComponent("vm://localhost?broker.persistent=false");
        amq.setCamelContext(context);
        answer.bind("jms", amq);
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jms:queue:inbox")
                    .convertBodyTo(String.class)
                    .doTry()
                        .to("validator:file:src/test/resources/myschema.xsd")
                        .to("jms:queue:valid")
                    .doCatch(ValidationException.class)
                        .to("jms:queue:invalid")
                    .doFinally()
                        .to("jms:queue:finally")
                    .end();
            }
        };
    }
}
