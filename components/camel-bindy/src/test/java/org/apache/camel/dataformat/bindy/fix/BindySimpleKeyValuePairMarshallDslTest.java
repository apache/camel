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
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.model.fix.simple.Order;
import org.apache.camel.model.dataformat.BindyType;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class BindySimpleKeyValuePairMarshallDslTest extends AbstractJUnit4SpringContextTests {

    private List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();
    private String result = "1=BE.CHM.00111=CHM0001-0122=448=BE0001245678"
        + "54=158=this is a camel - bindy test777=17-02-2011 23:29:59\r\n";

    @Produce(uri = "direct:start")
    private ProducerTemplate template;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint resultEndpoint;

    @Test
    public void testMarshallMessage() throws Exception {
        resultEndpoint.expectedBodiesReceived(result);
        template.sendBody(generateModel());

        resultEndpoint.assertIsSatisfied();
    }

    public List<Map<String, Object>> generateModel() {
        Map<String, Object> modelObjects = new HashMap<String, Object>();

        Order order = new Order();
        order.setAccount("BE.CHM.001");
        order.setClOrdId("CHM0001-01");
        order.setIDSource("4");
        order.setSecurityId("BE0001245678");
        order.setSide("1");
        order.setText("this is a camel - bindy test");

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        // 4 hour shift
        // 17-02-2011 23:29:59 by GMT-3
        calendar.set(2011, 1, 18, 2, 29, 59);
        order.setCreated(calendar.getTime());

        modelObjects.put(order.getClass().getName(), order);

        models.add(modelObjects);
        return models;
    }

    public static class ContextConfig extends RouteBuilder {
        
        public void configure() {
            from("direct:start").marshal()
                .bindy(BindyType.KeyValue, org.apache.camel.dataformat.bindy.model.fix.simple.Order.class)
                .to("mock:result");
        }

    }
}
