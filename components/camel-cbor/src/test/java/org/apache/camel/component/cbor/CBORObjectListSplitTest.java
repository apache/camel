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
package org.apache.camel.component.cbor;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CBORObjectListSplitTest extends CamelTestSupport {
    @Test
    public void testCBOR() throws InterruptedException, JsonProcessingException {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:result").expectedMessagesMatches(body().isInstanceOf(DummyObject.class));
        
        DummyObject d1 = new DummyObject();
        d1.setDummy("value1");
        DummyObject d2 = new DummyObject();
        d2.setDummy("value2");
        
        List<DummyObject> list = new ArrayList<>();
        list.add(d1);
        list.add(d2);

        CBORFactory factory = new CBORFactory();
        ObjectMapper objectMapper = new ObjectMapper(factory);
        byte[] payload = objectMapper.writeValueAsBytes(list);
        template.sendBody("direct:start", payload);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // you can specify the pojo class type for unmarshal the jason file
                CBORDataFormat format = new CBORDataFormat();
                format.useList();
                format.setUnmarshalType(DummyObject.class);
                from("direct:start").unmarshal(format).split(body()).to("mock:result");
            }
        };
    }
    
    public static class DummyObject {

        private String dummy;

        public DummyObject() {
        }

        public String getDummy() {
            return dummy;
        }

        public void setDummy(String dummy) {
            this.dummy = dummy;
        }
    }

}
