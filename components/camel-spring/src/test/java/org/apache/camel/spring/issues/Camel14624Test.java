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
package org.apache.camel.spring.issues;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

public class Camel14624Test extends CamelTestSupport {

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        TransactedPolicy required = new SpringTransactionPolicy(new MockTransactionManager());
        registry.bind("required", required);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:test")
                        .transacted("required")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testRoundtrip() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:test", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    public boolean isDumpRouteCoverage() {
        return true;
    }

    class MockTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            return null;
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
        }
    }
}