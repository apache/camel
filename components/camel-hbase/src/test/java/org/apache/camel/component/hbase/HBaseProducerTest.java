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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableExistsException;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HBaseProducerTest extends CamelHBaseTestSupport {

    @Before
    public void setUp() throws Exception {
        if (systemReady) {
            try {
                hbaseUtil.createTable(HBaseHelper.getHBaseFieldAsBytes(PERSON_TABLE), families);
            } catch (TableExistsException ex) {
                //Ignore if table exists
            }

            super.setUp();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (systemReady) {
            hbaseUtil.deleteTable(PERSON_TABLE.getBytes());
            super.tearDown();
        }
    }

    @Test
    public void testPut() throws Exception {
        if (systemReady) {
            ProducerTemplate template = context.createProducerTemplate();
            Map<String, Object> headers = new HashMap<String, Object>();
            headers.put(HbaseAttribute.HBASE_ROW_ID.asHeader(), key[0]);
            headers.put(HbaseAttribute.HBASE_FAMILY.asHeader(), family[0]);
            headers.put(HbaseAttribute.HBASE_QUALIFIER.asHeader(), column[0][0]);
            headers.put(HbaseAttribute.HBASE_VALUE.asHeader(), body[0][0][0]);
            headers.put(HBaseConstants.OPERATION, HBaseConstants.PUT);
            template.sendBodyAndHeaders("direct:start", null, headers);

            Configuration configuration = hbaseUtil.getHBaseAdmin().getConfiguration();
            HTable table = new HTable(configuration, PERSON_TABLE.getBytes());
            Get get = new Get(key[0].getBytes());

            get.addColumn(family[0].getBytes(), column[0][0].getBytes());
            Result result = table.get(get);
            byte[] resultValue = result.value();
            assertArrayEquals(body[0][0][0].getBytes(), resultValue);

            IOHelper.close(table);
        }
    }


    @Test
    public void testPutAndGet() throws Exception {
        testPut();
        if (systemReady) {
            ProducerTemplate template = context.createProducerTemplate();
            Endpoint endpoint = context.getEndpoint("direct:start");
            Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(), key[0]);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(), family[0]);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(), column[0][0]);
            exchange.getIn().setHeader(HBaseConstants.OPERATION, HBaseConstants.GET);
            Exchange resp = template.send(endpoint, exchange);
            assertEquals(body[0][0][0], resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader()));
        }
    }

    @Test
    public void testPutAndGetWithModel() throws Exception {
        if (systemReady) {
            ProducerTemplate template = context.createProducerTemplate();
            Endpoint startEndpoint = context.getEndpoint("direct:start");
            Endpoint startWithModelEndpoint = context.getEndpoint("direct:start-with-model");
            Exchange putExchange = startEndpoint.createExchange(ExchangePattern.InOut);

            int index = 1;
            for (int row = 0; row < key.length; row++) {
                for (int fam = 0; fam < family.length; fam++) {
                    for (int col = 0; col < column[fam].length; col++) {
                        putExchange.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(index), key[row]);
                        putExchange.getIn().setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(index), family[fam]);
                        putExchange.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(index), column[fam][col]);
                        putExchange.getIn().setHeader(HbaseAttribute.HBASE_VALUE.asHeader(index++), body[row][fam][col]);
                    }
                }
            }
            putExchange.getIn().setHeader(HBaseConstants.OPERATION, HBaseConstants.PUT);
            template.send(startEndpoint, putExchange);

            Exchange getExchange = startWithModelEndpoint.createExchange(ExchangePattern.InOut);
            getExchange.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(), key[0]);
            getExchange.getIn().setHeader(HBaseConstants.OPERATION, HBaseConstants.GET);
            Exchange resp = template.send(startWithModelEndpoint, getExchange);

            assertEquals(body[0][0][0], resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader()));
            assertEquals(body[0][1][2], resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader(2)));
        }
    }


    @Test
    public void testPutMultiRows() throws Exception {
        if (systemReady) {
            ProducerTemplate template = context.createProducerTemplate();
            Map<String, Object> headers = new HashMap<String, Object>();

            for (int row = 0; row < key.length; row++) {
                headers.put(HbaseAttribute.HBASE_ROW_ID.asHeader(row + 1), key[row]);
                headers.put(HbaseAttribute.HBASE_FAMILY.asHeader(row + 1), family[0]);
                headers.put(HbaseAttribute.HBASE_QUALIFIER.asHeader(row + 1), column[0][0]);
                headers.put(HbaseAttribute.HBASE_VALUE.asHeader(row + 1), body[row][0][0]);
            }

            headers.put(HBaseConstants.OPERATION, HBaseConstants.PUT);
            template.sendBodyAndHeaders("direct:start", null, headers);

            Configuration configuration = hbaseUtil.getHBaseAdmin().getConfiguration();
            HTable bar = new HTable(configuration, PERSON_TABLE.getBytes());

            //Check row 1
            for (int row = 0; row < key.length; row++) {
                Get get = new Get(key[row].getBytes());
                get.addColumn(family[0].getBytes(), column[0][0].getBytes());
                Result result = bar.get(get);
                byte[] resultValue = result.value();
                assertArrayEquals(body[row][0][0].getBytes(), resultValue);
            }

            IOHelper.close(bar);
        }
    }

    @Test
    public void testPutAndGetMultiRows() throws Exception {
        testPutMultiRows();
        if (systemReady) {
            ProducerTemplate template = context.createProducerTemplate();
            Endpoint endpoint = context.getEndpoint("direct:start");
            Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
            for (int row = 0; row < key.length; row++) {
                exchange.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(row + 1), key[row]);
                exchange.getIn().setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(row + 1), family[0]);
                exchange.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(row + 1), column[0][0]);
            }
            exchange.getIn().setHeader(HBaseConstants.OPERATION, HBaseConstants.GET);
            Exchange resp = template.send(endpoint, exchange);
            for (int row = 0; row < key.length; row++) {
                assertEquals(body[row][0][0], resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader(row + 1)));
            }
        }
    }


    @Test
    public void testPutMultiColumns() throws Exception {
        if (systemReady) {
            ProducerTemplate template = context.createProducerTemplate();
            Map<String, Object> headers = new HashMap<String, Object>();

            for (int col = 0; col < column[0].length; col++) {
                headers.put(HbaseAttribute.HBASE_ROW_ID.asHeader(col + 1), key[0]);
                headers.put(HbaseAttribute.HBASE_FAMILY.asHeader(col + 1), family[0]);
                headers.put(HbaseAttribute.HBASE_QUALIFIER.asHeader(col + 1), column[0][col]);
                headers.put(HbaseAttribute.HBASE_VALUE.asHeader(col + 1), body[0][col][0]);
            }

            headers.put(HBaseConstants.OPERATION, HBaseConstants.PUT);
            template.sendBodyAndHeaders("direct:start", null, headers);

            Configuration configuration = hbaseUtil.getHBaseAdmin().getConfiguration();
            HTable bar = new HTable(configuration, PERSON_TABLE.getBytes());

            for (int col = 0; col < column[0].length; col++) {
                Get get = new Get(key[0].getBytes());
                get.addColumn(family[0].getBytes(), column[0][col].getBytes());
                Result result = bar.get(get);
                byte[] resultValue = result.value();
                assertArrayEquals(body[0][col][0].getBytes(), resultValue);
            }

            IOHelper.close(bar);
        }
    }


    @Test
    public void testPutAndGetMultiColumns() throws Exception {
        testPutMultiColumns();
        if (systemReady) {
            ProducerTemplate template = context.createProducerTemplate();
            Endpoint endpoint = context.getEndpoint("direct:start");
            Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
            for (int col = 0; col < column[0].length; col++) {
                exchange.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(col + 1), key[0]);
                exchange.getIn().setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(col + 1), family[0]);
                exchange.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(col + 1), column[0][col]);
            }

            exchange.getIn().setHeader(HBaseConstants.OPERATION, HBaseConstants.GET);
            Exchange resp = template.send(endpoint, exchange);
            for (int col = 0; col < column[0].length; col++) {
                assertEquals(body[0][col][0], resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader(col + 1)));
            }
        }
    }


    @Test
    public void testPutAndGetAndDeleteMultiRows() throws Exception {
        testPutMultiRows();
        if (systemReady) {
            ProducerTemplate template = context.createProducerTemplate();
            Endpoint endpoint = context.getEndpoint("direct:start");

            Exchange exchange1 = endpoint.createExchange(ExchangePattern.InOnly);
            exchange1.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(), key[0]);
            exchange1.getIn().setHeader(HBaseConstants.OPERATION, HBaseConstants.DELETE);
            template.send(endpoint, exchange1);

            Exchange exchange2 = endpoint.createExchange(ExchangePattern.InOut);
            exchange2.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(), key[0]);
            exchange2.getIn().setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(), family[0]);
            exchange2.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(), column[0][0]);

            exchange2.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(2), key[1]);
            exchange2.getIn().setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(2), family[0]);
            exchange2.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(2), column[0][0]);
            exchange2.getIn().setHeader(HBaseConstants.OPERATION, HBaseConstants.GET);
            Exchange resp = template.send(endpoint, exchange2);
            assertEquals(null, resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader()));
            assertEquals(body[1][0][0], resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader(2)));
        }
    }

    @Test
    public void testPutMultiRowsAndScan() throws Exception {
        testPutMultiRows();
        if (systemReady) {
            ProducerTemplate template = context.createProducerTemplate();
            Endpoint endpoint = context.getEndpoint("direct:scan");

            Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(), family[0]);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(), column[0][0]);
            Exchange resp = template.send(endpoint, exchange);
            Object result1 = resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader(1));
            Object result2 = resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader(2));
            Object result3 = resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader(3));

            List<?> bodies = Arrays.asList(body[0][0][0], body[1][0][0], body[2][0][0]);
            assertTrue(bodies.contains(result1) && bodies.contains(result2) && bodies.contains(result3));

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

                from("direct:start-with-model")
                        .to("hbase://" + PERSON_TABLE + "?family=info&qualifier=firstName&family2=birthdate&qualifier2=year");

                from("direct:scan")
                        .to("hbase://" + PERSON_TABLE + "?operation=" + HBaseConstants.SCAN + "&maxResults=2");
            }
        };
    }
}
