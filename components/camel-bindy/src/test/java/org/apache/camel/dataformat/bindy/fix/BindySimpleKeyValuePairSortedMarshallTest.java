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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.bindy.CommonBindyTest;
import org.apache.camel.dataformat.bindy.kvp.BindyKeyValuePairDataFormat;
import org.apache.camel.dataformat.bindy.model.fix.sorted.body.Order;
import org.apache.camel.dataformat.bindy.model.fix.sorted.header.Header;
import org.apache.camel.dataformat.bindy.model.fix.sorted.trailer.Trailer;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration
public class BindySimpleKeyValuePairSortedMarshallTest extends CommonBindyTest {

    private static final Logger LOG = LoggerFactory.getLogger(BindySimpleKeyValuePairSortedMarshallTest.class);

    @Test
    @DirtiesContext
    public void testMarshallMessage() {

        String message = "8=FIX 4.19=2035=034=149=INVMGR56=BRKR1=BE.CHM.00122=411=CHM0001-0148=BE000124567854=158=this is a camel - bindy test10=220\r\n";

        result.expectedBodiesReceived(message);
        template.sendBody(generateModel());

        try {
            result.assertIsSatisfied();
        } catch (InterruptedException e) {
            LOG.error("Unit test error : ", e);
        }
    }

    public List<Map<String, Object>> generateModel() {

        List<Map<String, Object>> models = new ArrayList<>();
        Map<String, Object> modelObjects = new HashMap<>();

        Header header = new Header();
        header.setBeginString("FIX 4.1");
        header.setBodyLength(20);
        header.setMsgSeqNum(1);
        header.setMsgType("0");
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

        modelObjects.put(order.getClass().getName(), order);
        modelObjects.put(header.getClass().getName(), header);
        modelObjects.put(trailer.getClass().getName(), trailer);

        models.add(modelObjects);
        return models;
    }

    public static class ContextConfig extends RouteBuilder {

        BindyKeyValuePairDataFormat kvpBindyDataFormat = new BindyKeyValuePairDataFormat(org.apache.camel.dataformat.bindy.model.fix.sorted.body.Order.class);
       
        @Override
        public void configure() {
            from(URI_DIRECT_START).marshal(kvpBindyDataFormat).to(URI_MOCK_RESULT);
        }
    }
}
