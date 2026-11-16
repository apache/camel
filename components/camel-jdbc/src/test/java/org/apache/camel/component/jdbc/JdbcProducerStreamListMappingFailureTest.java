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
package org.apache.camel.component.jdbc;

import java.sql.SQLException;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With {@code outputType=StreamList} and {@code outputClass}, each row is mapped to the output class when the route
 * reads it. A row that cannot be mapped has already been read from the result set, so the iterator must not go on with
 * the next row: a split that is redelivered over it must fail again, not succeed without that row.
 */
public class JdbcProducerStreamListMappingFailureTest extends AbstractJdbcTestSupport {

    private static final String QUERY = "select * from item order by id";

    @EndpointInject("mock:row")
    private MockEndpoint row;

    @Override
    public void setupResources() {
        super.setupResources();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        jdbc.execute("create table item (id integer, status varchar(10))");
        // the status of the second row is not a constant of Status, so that row cannot be mapped to Item
        jdbc.execute("insert into item values (1, 'NEW'), (2, 'LOST'), (3, 'DONE')");
    }

    @Test
    public void rowThatCannotBeMappedFailsTheExchangeWhenTheSplitIsRedelivered() throws Exception {
        row.expectedMessageCount(1);

        Exchange out = template.send("direct:splitWithRedelivery", e -> e.getMessage().setBody(QUERY));

        // each redelivery of the split fails too, instead of going on with the row after the one that failed
        assertNotNull(out.getException(), () -> "the exchange succeeded with the rows " + routedIds());
        RuntimeCamelException failure = assertInstanceOf(RuntimeCamelException.class, out.getException());
        SQLException mapping = assertInstanceOf(SQLException.class, failure.getCause().getCause());
        assertTrue(mapping.getMessage().startsWith("Error setting properties on output class"), mapping.getMessage());
        assertEquals(2, out.getMessage().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class));
        MockEndpoint.assertIsSatisfied(context);
        assertEquals(List.of(1), routedIds());
    }

    private List<Integer> routedIds() {
        return row.getReceivedExchanges().stream().map(e -> e.getMessage().getBody(Item.class).getId()).toList();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:splitWithRedelivery")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                        .to("jdbc:testdb?outputType=StreamList&outputClass=" + Item.class.getName())
                        .split(body()).streaming()
                            .to("mock:row")
                        .end();
            }
        };
    }

    public enum Status {
        NEW,
        DONE
    }

    public static class Item {

        private int id;
        private Status status;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }
    }
}
