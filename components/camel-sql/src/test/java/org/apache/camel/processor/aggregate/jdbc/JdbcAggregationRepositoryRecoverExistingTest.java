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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JdbcAggregationRepositoryRecoverExistingTest extends AbstractJdbcAggregationTestSupport {

    @Override
    void configureJdbcAggregationRepository() {
        repo.setReturnOldExchange(true);
        repo.setUseRecovery(true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // no routes added by default
            }
        };
    }

    @Test
    public void testExisting() throws Exception {
        repo.start();

        // Store it..
        Exchange exchange1 = new DefaultExchange(context);
        exchange1.getIn().setBody("counter:1");
        Exchange actual = repo.add(context, "foo", exchange1);
        assertNull(actual);

        // Get and remove it, which makes it in the pre confirm stage
        exchange1 = repo.get(context, "foo");
        repo.remove(context, "foo", exchange1);

        String id = exchange1.getExchangeId();

        // stop the repo
        repo.stop();

        Thread.sleep(1000);

        // load the repo again
        repo.start();

        // Get it back..
        actual = repo.get(context, "foo");
        assertNull(actual);

        // Recover it
        actual = repo.recover(context, id);
        assertNotNull(actual);
        assertEquals("counter:1", actual.getIn().getBody());

        repo.stop();
    }
}
