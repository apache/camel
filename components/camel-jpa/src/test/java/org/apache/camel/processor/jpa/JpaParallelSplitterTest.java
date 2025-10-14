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
package org.apache.camel.processor.jpa;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.examples.SendEmail;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JpaParallelSplitterTest extends AbstractJpaTest {
    protected static final String SELECT_ALL_STRING = "select x from " + SendEmail.class.getName() + " x";

    @Test
    public void testParallelSplitter() throws Exception {
        Future<Object> future = template.asyncRequestBody("direct:splitter", "wrong@wrong.org");

        future.get(10, TimeUnit.SECONDS);

        assertCorrectEmails();
    }

    private void assertCorrectEmails() {
        List<?> results = entityManager
                .createQuery("select e from " + SendEmail.class.getName() + " e WHERE e.address = 'something@correct.org'")
                .getResultList();
        assertEquals(50, results.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                from("direct:splitter?timeout=1000")
                        .loop(50)
                        .setHeader("loopIndex", exchangeProperty(Exchange.LOOP_INDEX))
                        .process(ex -> {
                            int index = ex.getIn().getHeader("loopIndex", Integer.class);
                            SendEmail se = new SendEmail(index + "@wrong.org");
                            ex.getIn().setBody(se);
                        })
                        .to("jpa://" + SendEmail.class.getName())
                        .end()
                        //select all
                        .to("jpa://" + SendEmail.class.getName() + "?query=SELECT e FROM SendEmail e")
                        .split(body())
                        .parallelProcessing()
                        .threads(10, 10)
                        .process(ex -> {
                            SendEmail se = ex.getIn().getBody(SendEmail.class);
                            se.setAddress("something@correct.org");
                        })
                        .to("jpa://" + SendEmail.class.getName());

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
