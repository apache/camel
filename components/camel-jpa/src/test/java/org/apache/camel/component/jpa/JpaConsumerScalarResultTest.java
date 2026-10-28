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
package org.apache.camel.component.jpa;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.processor.jpa.AbstractJpaTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A query that selects values that are not entities (such as a column) delivers them with the default
 * consumeLockEntity=true and consumeDelete=true: there is no entity to lock or to delete.
 */
public class JpaConsumerScalarResultTest extends AbstractJpaTest {

    private static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";

    @Test
    public void testScalarResultsAreDelivered() throws Exception {
        saveEntityInDB(new SendEmail("bar@beer.org"));
        saveEntityInDB(new SendEmail("foo@beer.org"));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("bar@beer.org", "foo@beer.org");

        JpaConsumer consumer = (JpaConsumer) context.getRoute("scalar").getConsumer();
        // one poll in the test thread (the scheduler is not started)
        assertEquals(2, consumer.poll());

        mock.assertIsSatisfied();
        entityManager.clear();
        List<?> rows = entityManager.createQuery(SELECT_ALL_STRING).getResultList();
        assertEquals(2, rows.size(), "a value that is not an entity cannot be deleted, so the rows stay: " + rows);

        // the values were not deleted, so the next poll delivers them again
        assertEquals(2, consumer.poll());
        assertEquals(4, mock.getReceivedCounter());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jpa://" + SendEmail.class.getName() + "?query=select x.address from " + SendEmail.class.getName()
                     + " x&startScheduler=false").routeId("scalar")
                        .to("mock:result");
            }
        };
    }

    @Override
    protected String routeXml() {
        return "org/apache/camel/processor/jpa/springJpaRouteTest.xml";
    }

    @Override
    protected String selectAllString() {
        return SELECT_ALL_STRING;
    }
}
