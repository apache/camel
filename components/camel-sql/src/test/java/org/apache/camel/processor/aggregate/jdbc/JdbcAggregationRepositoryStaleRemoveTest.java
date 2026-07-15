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
import org.apache.camel.spi.OptimisticLockingAggregationRepository.OptimisticLockingException;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class JdbcAggregationRepositoryStaleRemoveTest extends AbstractJdbcAggregationTestSupport {

    @Override
    void configureJdbcAggregationRepository() {
        super.configureJdbcAggregationRepository();
        repo.setReturnOldExchange(true);
    }

    @Test
    public void testStaleRemoveThrowsOptimisticLockingException() {
        Exchange exchange1 = new DefaultExchange(context);
        exchange1.getIn().setBody("body1");
        repo.add(context, "foo", exchange1);

        // get exchange with version 1
        Exchange staleExchange = repo.get(context, "foo");

        // add again to bump the version in the database
        Exchange exchange2 = repo.get(context, "foo");
        exchange2.getIn().setBody("body2");
        repo.add(context, "foo", exchange2);

        // staleExchange still carries the old version — remove must detect the mismatch
        assertThrows(OptimisticLockingException.class,
                () -> repo.remove(context, "foo", staleExchange));
    }
}
