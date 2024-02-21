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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JdbcAggregationRepositoryTest extends AbstractJdbcAggregationTestSupport {

    @Override
    void configureJdbcAggregationRepository() {
        repo.setReturnOldExchange(true);
    }

    @Test
    public void testOperations() {
        // Can't get something we have not put in...
        Exchange actual = repo.get(context, "missing");
        assertNull(actual);

        // Store it..
        Exchange exchange1 = new DefaultExchange(context);
        exchange1.getIn().setBody("counter:1");
        actual = repo.add(context, "foo", exchange1);
        assertNull(actual);

        // Get it back..
        actual = repo.get(context, "foo");
        assertEquals("counter:1", actual.getIn().getBody());

        // Change it after reading the current exchange with version
        Exchange exchange2 = new DefaultExchange(context);
        exchange2 = repo.get(context, "foo");
        exchange2.getIn().setBody("counter:2");
        actual = repo.add(context, "foo", exchange2);
        // the old one
        assertEquals("counter:1", actual.getIn().getBody());

        // Get it back..
        actual = repo.get(context, "foo");
        assertEquals("counter:2", actual.getIn().getBody());

        // now remove it
        repo.remove(context, "foo", actual);
        actual = repo.get(context, "foo");
        assertNull(actual);

        // add it again
        exchange1 = new DefaultExchange(context);
        exchange1.getIn().setBody("counter:3");
        actual = repo.add(context, "foo", exchange1);
        assertNull(actual);

        // Get it back..
        actual = repo.get(context, "foo");
        assertEquals("counter:3", actual.getIn().getBody());
    }
}
