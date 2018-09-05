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
package org.apache.camel.dataformat.thrift;

import com.google.gson.Gson;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.thrift.generated.Operation;
import org.apache.camel.dataformat.thrift.generated.Work;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ThriftMarshalAndUnmarshalJsonTest extends CamelTestSupport {
    
    private static final String WORK_JSON_TEST = "{\"1\":{\"i32\":1},\"2\":{\"i32\":100},\"3\":{\"i32\":3},\"4\":{\"str\":\"This is a test thrift data\"}}";
    private static final String WORK_TEST_COMMENT = "This is a test thrift data";
    private static final int WORK_TEST_NUM1 = 1;
    private static final int WORK_TEST_NUM2 = 100;
    private static final Operation WORK_TEST_OPERATION = Operation.MULTIPLY;
    
    @Test
    public void testMarshalAndUnmarshal() throws Exception {
        marshalAndUnmarshal("direct:in", "direct:back");
    }
    
    @Test
    public void testMarshalAndUnmarshalWithDSL() throws Exception {
        marshalAndUnmarshal("direct:marshal", "direct:unmarshalA");
    }
    
    @Test
    public void testMarshalSimpleJson() throws Exception {
        Gson gson = new Gson();
        Work input = new Work();
        
        MockEndpoint mock = getMockEndpoint("mock:reverse-sjson");
        mock.expectedMessageCount(1);
        
        input.num1 = WORK_TEST_NUM1;
        input.num2 = WORK_TEST_NUM2;
        input.op = WORK_TEST_OPERATION;
        input.comment = WORK_TEST_COMMENT;
        
        template.requestBody("direct:marshal-sjson", input);
        mock.assertIsSatisfied();
        
        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        Work output = gson.fromJson(body, Work.class);
        
        assertEquals(WORK_TEST_NUM1, output.getNum1());
        assertEquals(WORK_TEST_NUM2, output.getNum2());
        assertEquals(WORK_TEST_COMMENT, output.getComment());
    }
    
    private void marshalAndUnmarshal(String inURI, String outURI) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Work.class);
        
        Object marshalled = template.requestBody(inURI, WORK_JSON_TEST);

        template.sendBody(outURI, marshalled);

        mock.assertIsSatisfied();

        Work output = mock.getReceivedExchanges().get(0).getIn().getBody(Work.class);
        
        assertEquals(WORK_TEST_NUM1, output.getNum1());
        assertEquals(WORK_TEST_NUM2, output.getNum2());
        assertEquals(WORK_TEST_OPERATION, output.getOp());
        assertEquals(WORK_TEST_COMMENT, output.getComment());
    }
    
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ThriftDataFormat format = new ThriftDataFormat(new Work(), ThriftDataFormat.CONTENT_TYPE_FORMAT_JSON);

                from("direct:in").unmarshal(format).to("mock:reverse");
                from("direct:back").marshal(format);

                from("direct:marshal").unmarshal().thrift("org.apache.camel.dataformat.thrift.generated.Work", "json").to("mock:reverse");
                from("direct:unmarshalA").marshal().thrift();
                
                from("direct:marshal-sjson").marshal().thrift("org.apache.camel.dataformat.thrift.generated.Work", "sjson").convertBodyTo(String.class).to("mock:reverse-sjson");
            }
        };
    }
}
