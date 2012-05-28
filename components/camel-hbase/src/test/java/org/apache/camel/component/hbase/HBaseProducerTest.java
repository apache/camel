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
import org.apache.camel.component.hbase.mapping.CellMappingStrategyFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableExistsException;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HBaseProducerTest extends CamelHBaseTestSupport {

    protected String[] key = {"1", "2", "3"};
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
            hbaseUtil.deleteTable(DEFAULTTABLE.getBytes());
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
            headers.put(HbaseAttribute.HBASE_QUALIFIER.asHeader(), column[0]);
            headers.put(HbaseAttribute.HBASE_VALUE.asHeader(), body[0]);
            headers.put(HBaseContats.OPERATION, HBaseContats.PUT);
            template.sendBodyAndHeaders("direct:start", null, headers);

            Configuration configuration = hbaseUtil.getHBaseAdmin().getConfiguration();
            HTable table = new HTable(configuration, DEFAULTTABLE.getBytes());
            Get get = new Get(key[0].getBytes());

            get.addColumn(family[0].getBytes(), column[0].getBytes());
            Result result = table.get(get);
            byte[] resultValue = result.value();
            assertArrayEquals(body[0].getBytes(), resultValue);
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
            exchange.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(), column[0]);
            exchange.getIn().setHeader(HBaseContats.OPERATION, HBaseContats.GET);
            Exchange resp = template.send(endpoint, exchange);
            assertEquals(body[0], resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader()));
        }
    }


    @Test
    public void testPutMultiRows() throws Exception {
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

            Configuration configuration = hbaseUtil.getHBaseAdmin().getConfiguration();
            HTable bar = new HTable(configuration, DEFAULTTABLE.getBytes());
            Get get = new Get(key[0].getBytes());

            //Check row 1
            get.addColumn(family[0].getBytes(), column[0].getBytes());
            Result result = bar.get(get);
            byte[] resultValue = result.value();
            assertArrayEquals(body[0].getBytes(), resultValue);

            //Check row 2
            get = new Get(key[1].getBytes());
            get.addColumn(family[0].getBytes(), column[0].getBytes());
            result = bar.get(get);
            resultValue = result.value();
            assertArrayEquals(body[1].getBytes(), resultValue);

            //Check row 3
            get = new Get(key[2].getBytes());
            get.addColumn(family[0].getBytes(), column[0].getBytes());
            result = bar.get(get);
            resultValue = result.value();
            assertArrayEquals(body[2].getBytes(), resultValue);
        }
    }

    @Test
    public void testPutAndGetMultiRows() throws Exception {
        testPutMultiRows();
        if (systemReady) {
            ProducerTemplate template = context.createProducerTemplate();
            Endpoint endpoint = context.getEndpoint("direct:start");
            Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(), key[0]);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(), family[0]);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(), column[0]);

            exchange.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(2), key[1]);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(2), family[0]);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(2), column[0]);

            exchange.getIn().setHeader(HBaseContats.OPERATION, HBaseContats.GET);
            Exchange resp = template.send(endpoint, exchange);
            assertEquals(body[0], resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader()));
            assertEquals(body[1], resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader(2)));
        }
    }


    @Test
    public void testPutMultiColumns() throws Exception {
        if (systemReady) {
            ProducerTemplate template = context.createProducerTemplate();
            Map<String, Object> headers = new HashMap<String, Object>();
            headers.put(HbaseAttribute.HBASE_ROW_ID.asHeader(), key[0]);
            headers.put(HbaseAttribute.HBASE_FAMILY.asHeader(), family[0]);
            headers.put(HbaseAttribute.HBASE_QUALIFIER.asHeader(), column[0]);
            headers.put(HbaseAttribute.HBASE_VALUE.asHeader(), body[0]);

            headers.put(HbaseAttribute.HBASE_ROW_ID.asHeader(2), key[0]);
            headers.put(HbaseAttribute.HBASE_FAMILY.asHeader(2), family[1]);
            headers.put(HbaseAttribute.HBASE_QUALIFIER.asHeader(2), column[1]);
            headers.put(HbaseAttribute.HBASE_VALUE.asHeader(2), body[1]);

            headers.put(HBaseContats.OPERATION, HBaseContats.PUT);

            template.sendBodyAndHeaders("direct:start", null, headers);

            Configuration configuration = hbaseUtil.getHBaseAdmin().getConfiguration();
            HTable bar = new HTable(configuration, DEFAULTTABLE.getBytes());
            Get get = new Get(key[0].getBytes());

            //Check column 1
            get.addColumn(family[0].getBytes(), column[0].getBytes());
            Result result = bar.get(get);
            byte[] resultValue = result.value();
            assertArrayEquals(body[0].getBytes(), resultValue);

            //Check column 2
            get = new Get(key[0].getBytes());
            get.addColumn(family[1].getBytes(), column[1].getBytes());
            result = bar.get(get);
            resultValue = result.value();
            assertArrayEquals(body[1].getBytes(), resultValue);
        }
    }


    @Test
    public void testPutAndGetMultiColumns() throws Exception {
        testPutMultiColumns();
        if (systemReady) {
            ProducerTemplate template = context.createProducerTemplate();
            Endpoint endpoint = context.getEndpoint("direct:start");
            Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(), key[0]);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(), family[0]);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(), column[0]);

            exchange.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(2), key[0]);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(2), family[1]);
            exchange.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(2), column[1]);

            exchange.getIn().setHeader(HBaseContats.OPERATION, HBaseContats.GET);
            Exchange resp = template.send(endpoint, exchange);
            assertEquals(body[0], resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader()));
            assertEquals(body[1], resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader(2)));
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
            exchange1.getIn().setHeader(HBaseContats.OPERATION, HBaseContats.DELETE);
            template.send(endpoint, exchange1);

            Exchange exchange2 = endpoint.createExchange(ExchangePattern.InOut);
            exchange2.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(), key[0]);
            exchange2.getIn().setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(), family[0]);
            exchange2.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(), column[0]);

            exchange2.getIn().setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(2), key[1]);
            exchange2.getIn().setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(2), family[0]);
            exchange2.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(2), column[0]);
            exchange2.getIn().setHeader(HBaseContats.OPERATION, HBaseContats.GET);
            Exchange resp = template.send(endpoint, exchange2);
            assertEquals(null, resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader()));
            assertEquals(body[1], resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader(2)));
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
            exchange.getIn().setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(), column[0]);
            Exchange resp = template.send(endpoint, exchange);
            Object result1 = resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader(1));
            Object result2 = resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader(2));
            Object result3 = resp.getOut().getHeader(HbaseAttribute.HBASE_VALUE.asHeader(3));

            List bodies = Arrays.asList(body);
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
                        .to("hbase://" + DEFAULTTABLE);

                from("direct:scan")
                        .to("hbase://" + DEFAULTTABLE + "?operation=" + HBaseContats.SCAN + "&maxResults=2&family=family1&qualifier=column1");
            }
        };
    }
}
