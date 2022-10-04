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

package org.apache.camel.component.cassandra.integration;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.resume.TransientResumeStrategy;
import org.apache.camel.resume.ResumeAction;
import org.apache.camel.resume.ResumeActionAware;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.cassandra.CassandraConstants.CASSANDRA_RESUME_ACTION;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CassandraComponentResumeStrategyIT extends BaseCassandra {

    private static class TestCassandraResumeAdapter implements ResumeActionAware {
        private boolean resumeCalled;
        private boolean resumeActionNotNull;

        @Override
        public void setResumeAction(ResumeAction action) {
            resumeActionNotNull = action != null;
        }

        @Override
        public void resume() {
            resumeCalled = true;
        }
    }

    private static final String CQL = "select login, first_name, last_name from camel_user";
    private final TestCassandraResumeAdapter resumeStrategy = new TestCassandraResumeAdapter();

    @Test
    public void testConsumeAll() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultAll");
        mock.expectedMinimumMessageCount(1);
        mock.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                assertTrue(body instanceof List);
            }
        });
        mock.await(1, TimeUnit.SECONDS);
        MockEndpoint.assertIsSatisfied(context);

        assertTrue(resumeStrategy.resumeActionNotNull);
        assertTrue(resumeStrategy.resumeCalled);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                bindToRegistry(CASSANDRA_RESUME_ACTION, (ResumeAction) (key, value) -> true);

                fromF("cql://%s/%s?cql=%s", getUrl(), KEYSPACE_NAME, CQL)
                        .resumable(new TransientResumeStrategy(resumeStrategy))
                        .to("mock:resultAll");
            }
        };
    }
}
