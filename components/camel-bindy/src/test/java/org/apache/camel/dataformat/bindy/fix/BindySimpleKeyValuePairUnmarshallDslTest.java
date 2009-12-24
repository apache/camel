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
package org.apache.camel.dataformat.bindy.fix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.bindy.CommonBindyTest;
import org.apache.camel.dataformat.bindy.model.fix.simple.Header;
import org.apache.camel.dataformat.bindy.model.fix.simple.Order;
import org.apache.camel.dataformat.bindy.model.fix.simple.Trailer;
import org.apache.camel.model.dataformat.BindyType;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration
public class BindySimpleKeyValuePairUnmarshallDslTest extends CommonBindyTest {

    @Test
    @DirtiesContext
    public void testUnMarshallMessage() throws Exception {

        result.expectedMessageCount(1);
        result.expectedBodiesReceived(generateModel().toString());
        result.assertIsSatisfied();
    }

    public List<Map<String, Object>> generateModel() {
        List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();
        Map<String, Object> model = new HashMap<String, Object>();

        Header header = new Header();
        header.setBeginString("FIX.4.1");
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
        order.setText("this is a camel - bindy test");
        order.setIDSource("4");
        order.setSecurityId("BE0001245678");
        order.setSide("1");

        order.setHeader(header);
        order.setTrailer(trailer);

        model.put(order.getClass().getName(), order);
        model.put(header.getClass().getName(), header);
        model.put(trailer.getClass().getName(), trailer);

        models.add(model);
        return models;
    }

    public static class ContextConfig extends RouteBuilder {

        public void configure() {
            from(URI_FILE_FIX).unmarshal()
                .bindy(BindyType.KeyValue, "org.apache.camel.dataformat.bindy.model.fix.simple")
                .to(URI_MOCK_RESULT);
        }

    }

}
