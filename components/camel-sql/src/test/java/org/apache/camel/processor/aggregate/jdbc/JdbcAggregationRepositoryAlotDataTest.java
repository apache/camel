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
package org.apache.camel.processor.aggregate.jdbc;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Test;

public class JdbcAggregationRepositoryAlotDataTest extends AbstractJdbcAggregationTestSupport {

    @Test
    public void testWithAlotOfDataSameKey() {
        for (int i = 0; i < 100; i++) {
            Exchange exchange1 = new DefaultExchange(context);
            exchange1.getIn().setBody("counter:" + i);
            repo.add(context, "foo", exchange1);
        }

        // Get it back..
        Exchange actual = repo.get(context, "foo");
        assertEquals("counter:99", actual.getIn().getBody());
    }

    @Test
    public void testWithAlotOfDataTwoKeys() {
        for (int i = 0; i < 100; i++) {
            Exchange exchange1 = new DefaultExchange(context);
            exchange1.getIn().setBody("counter:" + i);
            String key = i % 2 == 0 ? "foo" : "bar";
            repo.add(context, key, exchange1);
        }

        // Get it back..
        Exchange actual = repo.get(context, "foo");
        assertEquals("counter:98", actual.getIn().getBody());

        actual = repo.get(context, "bar");
        assertEquals("counter:99", actual.getIn().getBody());
    }

    @Test
    public void testWithAlotOfDataWithDifferentKeys() {
        for (int i = 0; i < 100; i++) {
            Exchange exchange1 = new DefaultExchange(context);
            exchange1.getIn().setBody("counter:" + i);
            String key = "key" + i;
            repo.add(context, key, exchange1);
        }

        // Get it back..
        for (int i = 0; i < 100; i++) {
            Exchange actual = repo.get(context, "key" + i);
            assertEquals("counter:" + i, actual.getIn().getBody());
        }
    }
}