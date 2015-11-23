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
package org.apache.camel.component.jms;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentTransacted;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version
 */
public class JmsChunkCountTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(JmsChunkCountTest.class);

    @Test
    public void test() throws Exception {
        template.sendBody("jms:queue:hello?maxChunkSize=3", "Hello World");
        String pieceId;
        Exchange ex = consumer.receive("jms:queue:hello", 5000);
        assertEquals(1, ex.getIn().getHeader("CamelJmsCounter"));
        pieceId = (String) ex.getIn().getHeader("CamelJmsChunkCollectionId");
        ex = consumer.receive("jms:queue:hello", 5000);
        assertEquals(2, ex.getIn().getHeader("CamelJmsCounter"));
        assertEquals(pieceId, ex.getIn().getHeader("CamelJmsChunkCollectionId"));
        ex = consumer.receive("jms:queue:hello", 5000);
        assertEquals(3, ex.getIn().getHeader("CamelJmsCounter"));
        assertEquals(pieceId, ex.getIn().getHeader("CamelJmsChunkCollectionId"));
        ex = consumer.receive("jms:queue:hello", 5000);
        assertEquals("CamelJmsHead", ex.getIn().getHeader("CamelJmsHead"));
        assertEquals(pieceId, ex.getIn().getHeader("CamelJmsChunkCollectionId"));
        assertEquals(3, ex.getIn().getHeader("CamelJmsCount"));
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createPersistentConnectionFactory();
        JmsComponent component = jmsComponentTransacted(connectionFactory);
        camelContext.addComponent("jms", component);

        return camelContext;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
