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
package org.apache.camel.component.hbase;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class HBaseConvertionsTest extends CamelHBaseTestSupport {

    protected Object[] key = {1, "2", "3"};
    protected final Object[] body = {1L, false, "3"};
    protected final String[] column = {"DEFAULTCOLUMN"};
    protected final byte[][] families = {INFO_FAMILY.getBytes()};

    @Test
    public void testPutMultiRows() throws Exception {
        ProducerTemplate template = context.createProducerTemplate();
        Map<String, Object> headers = new HashMap<>();
        headers.put(HBaseAttribute.HBASE_ROW_ID.asHeader(), key[0]);
        headers.put(HBaseAttribute.HBASE_FAMILY.asHeader(), INFO_FAMILY);
        headers.put(HBaseAttribute.HBASE_QUALIFIER.asHeader(), column[0]);
        headers.put(HBaseAttribute.HBASE_VALUE.asHeader(), body[0]);

        headers.put(HBaseAttribute.HBASE_ROW_ID.asHeader(2), key[1]);
        headers.put(HBaseAttribute.HBASE_FAMILY.asHeader(2), INFO_FAMILY);
        headers.put(HBaseAttribute.HBASE_QUALIFIER.asHeader(2), column[0]);
        headers.put(HBaseAttribute.HBASE_VALUE.asHeader(2), body[1]);

        headers.put(HBaseAttribute.HBASE_ROW_ID.asHeader(3), key[2]);
        headers.put(HBaseAttribute.HBASE_FAMILY.asHeader(3), INFO_FAMILY);
        headers.put(HBaseAttribute.HBASE_QUALIFIER.asHeader(3), column[0]);
        headers.put(HBaseAttribute.HBASE_VALUE.asHeader(3), body[2]);

        headers.put(HBaseConstants.OPERATION, HBaseConstants.PUT);

        template.sendBodyAndHeaders("direct:start", null, headers);

        Connection conn = connectHBase();
        Table bar = conn.getTable(TableName.valueOf(PERSON_TABLE));
        Get get = new Get(Bytes.toBytes((Integer) key[0]));

        //Check row 1
        get.addColumn(INFO_FAMILY.getBytes(), column[0].getBytes());
        Result result = bar.get(get);
        byte[] resultValue = result.value();
        assertArrayEquals(Bytes.toBytes((Long) body[0]), resultValue);

        //Check row 2
        get = new Get(Bytes.toBytes((String) key[1]));
        get.addColumn(INFO_FAMILY.getBytes(), column[0].getBytes());
        result = bar.get(get);
        resultValue = result.value();
        assertArrayEquals(Bytes.toBytes((Boolean) body[1]), resultValue);

        //Check row 3
        get = new Get(Bytes.toBytes((String) key[2]));
        get.addColumn(INFO_FAMILY.getBytes(), column[0].getBytes());
        result = bar.get(get);
        resultValue = result.value();
        assertArrayEquals(Bytes.toBytes((String) body[2]), resultValue);

        IOHelper.close(bar);
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
                from("direct:scan")
                        .to("hbase://" + PERSON_TABLE + "?operation=" + HBaseConstants.SCAN + "&maxResults=2&row.family=family1&row.qualifier=column1");
            }
        };
    }
}
