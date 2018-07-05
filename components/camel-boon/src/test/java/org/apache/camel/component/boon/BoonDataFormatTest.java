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
package org.apache.camel.component.boon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class BoonDataFormatTest extends CamelTestSupport {

    @Test
    public void testMarshalAndUnmarshalMap() throws Exception {
        Map<String, String> in = new LinkedHashMap<>();
        in.put("name", "Camel");

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Map.class);
        mock.message(0).body().isEqualTo(in);

        Object marshalled = template.requestBody("direct:in", in);
        String marshalledAsString = context.getTypeConverter().convertTo(String.class, marshalled);
        assertEquals("{\"name\":\"Camel\"}", marshalledAsString);

        template.sendBody("direct:back", marshalled);

        mock.assertIsSatisfied();
    }

    @Test
    public void testMarshalAndUnmarshalPojo() throws Exception {
        TestPojo in = new TestPojo();
        in.setName("Camel");

        MockEndpoint mock = getMockEndpoint("mock:reversePojo");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(TestPojo.class);
        mock.message(0).body().isEqualTo(in);

        Object marshalled = template.requestBody("direct:inPojo", in);
        String marshalledAsString = context.getTypeConverter().convertTo(String.class, marshalled);
        assertEquals("{\"name\":\"Camel\"}", marshalledAsString);

        template.sendBody("direct:backPojo", marshalled);

        mock.assertIsSatisfied();
    }
    
    @Test
    public void testMarshalAndUnmarshalList() throws Exception {
        List<String> in = new ArrayList<>();
        in.add("Karaf");
        in.add("Camel");
        in.add("Servicemix");

        MockEndpoint mock = getMockEndpoint("mock:reverseList");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(List.class);
        mock.message(0).body().isEqualTo(in);

        Object marshalled = template.requestBody("direct:inList", in);
        String marshalledAsString = context.getTypeConverter().convertTo(String.class, marshalled);
        assertEquals("[\"Karaf\",\"Camel\",\"Servicemix\"]", marshalledAsString);

        template.sendBody("direct:backList", marshalled);

        mock.assertIsSatisfied();
    }
    
    @Test
    public void testMarshalAndUnmarshalPojoMap() throws Exception {
        TestPojo in = new TestPojo();
        in.setName("Camel");
        
        Map<String, TestPojo> map = new LinkedHashMap<>();
        map.put("test1", in);
        map.put("test2", in);

        MockEndpoint mock = getMockEndpoint("mock:reversePojosMap");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Map.class);

        Object marshalled = template.requestBody("direct:inPojosMap", map);
        String marshalledAsString = context.getTypeConverter().convertTo(String.class, marshalled);
        assertEquals("{\"test1\":{\"name\":\"Camel\"},\"test2\":{\"name\":\"Camel\"}}", marshalledAsString);

        template.sendBody("direct:backPojosMap", marshalled);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                BoonDataFormat format = new BoonDataFormat();

                from("direct:in").marshal(format);
                from("direct:back").unmarshal(format).to("mock:reverse");

                BoonDataFormat formatPojo = new BoonDataFormat(TestPojo.class);

                from("direct:inPojo").marshal(formatPojo);
                from("direct:backPojo").unmarshal(formatPojo).to("mock:reversePojo");
                
                BoonDataFormat formatList = new BoonDataFormat();
                formatList.setUseList(true);

                from("direct:inList").marshal(formatList);
                from("direct:backList").unmarshal(formatList).to("mock:reverseList");
                
                BoonDataFormat formatPojoMaps = new BoonDataFormat();

                from("direct:inPojosMap").marshal(formatPojoMaps);
                from("direct:backPojosMap").unmarshal(formatPojoMaps).to("mock:reversePojosMap");
            }
        };
    }

}
