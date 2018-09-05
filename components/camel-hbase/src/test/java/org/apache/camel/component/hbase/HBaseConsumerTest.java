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
package org.apache.camel.component.hbase;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class HBaseConsumerTest extends CamelHBaseTestSupport {

    @Test
    public void testPutMultiRowsAndConsume() throws Exception {
        if (systemReady) {
            MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
            mockEndpoint.expectedMessageCount(3);

            Map<String, Object> headers = new HashMap<>();
            for (int row = 0; row < key.length; row++) {
                headers.put(HBaseAttribute.HBASE_ROW_ID.asHeader(row + 1), key[row]);
                headers.put(HBaseAttribute.HBASE_FAMILY.asHeader(row + 1), family[0]);
                headers.put(HBaseAttribute.HBASE_QUALIFIER.asHeader(row + 1), column[0][0]);
                headers.put(HBaseAttribute.HBASE_VALUE.asHeader(row + 1), body[row][0][0]);
            }
            headers.put(HBaseConstants.OPERATION, HBaseConstants.PUT);

            template.sendBodyAndHeaders("direct:start", null, headers);

            mockEndpoint.assertIsSatisfied();
        }
    }

    /**
     * Factory method which derived classes can use to create a {@link org.apache.camel.builder.RouteBuilder}
     * to define the routes for testing
     */
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                    .to("hbase://" + PERSON_TABLE);
                from("hbase://" + PERSON_TABLE)
                    .to("mock:result");
            }
        };
    }
}
