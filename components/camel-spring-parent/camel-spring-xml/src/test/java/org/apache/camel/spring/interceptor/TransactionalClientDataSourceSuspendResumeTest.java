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
package org.apache.camel.spring.interceptor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that transacted routes commit correctly after context.suspend() + context.resume().
 *
 * During suspend, DefaultShutdownStrategy sets preparingShutdown = true on TransactionErrorHandler via
 * getChildServices(service, includeErrorHandler=true). During resume, the error handler is excluded from the service
 * list (includeErrorHandler=false) and its status stays STARTED, so neither doStart() nor doResume() is called to reset
 * the flag. Subsequent transacted exchanges are silently rolled back.
 */
public class TransactionalClientDataSourceSuspendResumeTest extends TransactionClientDataSourceSupport {

    @Test
    public void testTransactionCommitsAfterContextSuspendResume() throws Exception {
        // baseline: one book inserted via init.sql
        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(1, count, "Initial number of books");

        // first send — must commit
        template.sendBody("direct:suspend-resume", "Tiger in Action");
        count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(2, count, "After first send");

        // suspend and resume the entire context
        context.suspend();
        context.resume();

        // second send — must also commit if preparingShutdown was properly reset
        template.sendBody("direct:suspend-resume", "Elephant in Action");
        count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(3, count,
                "After context suspend/resume: transaction should commit but preparingShutdown"
                               + " is still true, causing silent rollback");
    }

    @Test
    public void testTransactionCommitsAfterRouteSuspendResume() throws Exception {
        // baseline: one book inserted via init.sql
        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(1, count, "Initial number of books");

        // first send — must commit
        template.sendBody("direct:suspend-resume", "Tiger in Action");
        count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(2, count, "After first send");

        // suspend and resume a single route
        context.getRouteController().suspendRoute("suspendResumeRoute");
        context.getRouteController().resumeRoute("suspendResumeRoute");

        // second send — must also commit
        template.sendBody("direct:suspend-resume", "Elephant in Action");
        count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(3, count, "After route suspend/resume: transaction must commit");
    }

    @Override
    @SuppressWarnings("deprecation")
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                SpringTransactionPolicy required = lookup("PROPAGATION_REQUIRED", SpringTransactionPolicy.class);
                errorHandler(transactionErrorHandler(required));

                from("direct:suspend-resume").routeId("suspendResumeRoute")
                        .policy(required)
                        .bean("bookService");
            }
        };
    }
}
