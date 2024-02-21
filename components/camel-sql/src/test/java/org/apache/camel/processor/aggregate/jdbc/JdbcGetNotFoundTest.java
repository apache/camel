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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JdbcGetNotFoundTest extends AbstractJdbcAggregationTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcGetNotFoundTest.class);

    @Test
    public void testGetNotFound() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");

        Exchange out = repo.get(context, exchange.getExchangeId());
        assertNull(out, "Should not find exchange");
    }

    @Test
    public void testPutAndGetNotFound() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");
        LOG.info("Created {}", exchange.getExchangeId());

        repo.add(context, exchange.getExchangeId(), exchange);
        Exchange out = repo.get(context, exchange.getExchangeId());
        assertNotNull(out, "Should find exchange");

        Exchange exchange2 = new DefaultExchange(context);
        exchange2.getIn().setBody("Bye World");
        LOG.info("Created {}", exchange2.getExchangeId());

        Exchange out2 = repo.get(context, exchange2.getExchangeId());
        assertNull(out2, "Should not find exchange");
    }
}
