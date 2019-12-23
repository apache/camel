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
package org.apache.camel.dataformat.bindy.fix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.kvp.BindyKeyValuePairDataFormat;
import org.apache.camel.dataformat.bindy.model.fix.simple.Header;
import org.apache.camel.dataformat.bindy.model.fix.simple.Order;
import org.apache.camel.dataformat.bindy.model.fix.simple.Trailer;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class BindySimpleKeyValuePairNullMarshallTest extends AbstractJUnit4SpringContextTests {

    @Produce("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint resultEndpoint;

    @Test
    @DirtiesContext
    public void testMarshallMessage() throws Exception {

        String result = "1=BE.CHM.0018=FIX 4.19=2010=22011=CHM0001-0122=434=148=BE000124567849=INVMGR54=156=BRKR58=this is a camel - bindy test\r\n";

        resultEndpoint.expectedBodiesReceived(result);
        template.sendBody(generateModel());

        resultEndpoint.assertIsSatisfied();
    }

    public List<Map<String, Object>> generateModel() {
        List<Map<String, Object>> models = new ArrayList<>();
        Map<String, Object> model = new HashMap<>();

        Header header = new Header();
        header.setBeginString("FIX 4.1");
        header.setBodyLength(20);
        header.setMsgSeqNum(1);
        header.setMsgType(null); // NULL value
        header.setSendCompId("INVMGR");
        header.setTargetCompId("BRKR");

        Trailer trailer = new Trailer();
        trailer.setCheckSum(220);

        Order order = new Order();
        order.setAccount("BE.CHM.001");
        order.setClOrdId("CHM0001-01");
        order.setIDSource("4");
        order.setSecurityId("BE0001245678");
        order.setSide("1");
        order.setText("this is a camel - bindy test");

        order.setHeader(header);
        order.setTrailer(trailer);

        model.put(order.getClass().getName(), order);
        model.put(header.getClass().getName(), header);
        model.put(trailer.getClass().getName(), trailer);

        models.add(model);
        return models;
    }

    public static class ContextConfig extends RouteBuilder {
        BindyKeyValuePairDataFormat kvpBindyDataFormat = new BindyKeyValuePairDataFormat(org.apache.camel.dataformat.bindy.model.fix.simple.Order.class);

        @Override
        public void configure() {
            from("direct:start").marshal(kvpBindyDataFormat).to("mock:result");
        }

    }
}
