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
package org.apache.camel.processor.aggregate.jdbc;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdbcGrowIssueTest extends AbstractJdbcAggregationTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcGrowIssueTest.class);

    private static final int SIZE = 1024;

    @Test
    public void testGrowIssue() {
        // a 1kb string for testing
        StringBuilder sb = new StringBuilder(SIZE);
        for (int i = 0; i < SIZE; i++) {
            sb.append("X");
        }
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(sb.toString(), String.class);

        // the key
        final String key = "foo";

        // we update using the same key, which means we should be able to do this within the file size limit
        for (int i = 0; i < SIZE; i++) {
            LOG.debug("Updating {}", i);
            exchange = repoAddAndGet(key, exchange);
        }

        // get the last
        Exchange data = repo.get(context, key);
        LOG.info(data.toString());

        assertTrue(data.getIn().getBody(String.class).startsWith("XXX"), "Should start with 'XXX'");
        int length = data.getIn().getBody(String.class).length();
        assertEquals(1024, length, "Length should be 1024, was " + length);
    }
}
