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
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.hadoop.hbase.TableExistsException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HBaseConsumerTest extends CamelHBaseTestSupport {

    protected Object[] key = {"1", "2", "3"};
    protected final String[] body = {"Hello Hbase", "Hi HBase", "Yo HBase"};
    protected final String[] family = {"family1", "family2", "family3"};
    protected final String[] column = {"mycolumn1", "mycolumn2", "mycolumn3"};
    protected final byte[][] families = {DEFAULTFAMILY.getBytes(),
            family[0].getBytes(),
            family[1].getBytes(),
            family[2].getBytes()};

    @Before
    public void setUp() throws Exception {
        if (systemReady) {
            try {
                hbaseUtil.createTable(HBaseHelper.getHBaseFieldAsBytes(DEFAULTTABLE), families);
            } catch (TableExistsException ex) {
                //Ignore if table exists
            }

            super.setUp();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (systemReady) {
            super.tearDown();
        }
    }


    @Test
    public void testPutMultiRowsAndConsume() throws Exception {
        if (systemReady) {
            ProducerTemplate template = context.createProducerTemplate();
            Map<String, Object> headers = new HashMap<String, Object>();
            headers.put(HbaseAttribute.HBASE_ROW_ID.asHeader(), key[0]);
            headers.put(HbaseAttribute.HBASE_FAMILY.asHeader(), family[0]);
            headers.put(HbaseAttribute.HBASE_QUALIFIER.asHeader(), column[0]);
            headers.put(HbaseAttribute.HBASE_VALUE.asHeader(), body[0]);

            headers.put(HbaseAttribute.HBASE_ROW_ID.asHeader(2), key[1]);
            headers.put(HbaseAttribute.HBASE_FAMILY.asHeader(2), family[0]);
            headers.put(HbaseAttribute.HBASE_QUALIFIER.asHeader(2), column[0]);
            headers.put(HbaseAttribute.HBASE_VALUE.asHeader(2), body[1]);

            headers.put(HbaseAttribute.HBASE_ROW_ID.asHeader(3), key[2]);
            headers.put(HbaseAttribute.HBASE_FAMILY.asHeader(3), family[0]);
            headers.put(HbaseAttribute.HBASE_QUALIFIER.asHeader(3), column[0]);
            headers.put(HbaseAttribute.HBASE_VALUE.asHeader(3), body[2]);

            headers.put(HBaseContats.OPERATION, HBaseContats.PUT);

            template.sendBodyAndHeaders("direct:start", null, headers);

            MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
            mockEndpoint.expectedMessageCount(3);
            mockEndpoint.assertIsSatisfied(10000);
            Thread.sleep(10000);
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
                        .to("hbase://" + DEFAULTTABLE);

                from("hbase://" + DEFAULTTABLE)
                        .to("mock:result");
            }
        };
    }
}
