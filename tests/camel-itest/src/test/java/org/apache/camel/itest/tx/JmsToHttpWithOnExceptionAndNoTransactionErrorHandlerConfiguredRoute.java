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
package org.apache.camel.itest.tx;

import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.test.AvailablePortFinder;

/**
 * Route that listen on a JMS queue and send a request/reply over http before returning a response. Is transacted.
 * <p/>
 * Notice we use the SpringRouteBuilder that supports transacted error handler.
 */
public class JmsToHttpWithOnExceptionAndNoTransactionErrorHandlerConfiguredRoute extends JmsToHttpRoute {
    private String noAccess = "<?xml version=\"1.0\"?><reply><status>Access denied</status></reply>";

    @Override
    public void configure() {
        port = AvailablePortFinder.getNextAvailable();

        // if its a 404 then regard it as handled
        onException(HttpOperationFailedException.class).onWhen(exchange -> {
            HttpOperationFailedException e = exchange.getException(HttpOperationFailedException.class);
            return e != null && e.getStatusCode() == 404;
        }).handled(true).to("mock:404").transform(constant(noAccess));

        from("activemq:queue:JmsToHttpWithOnExceptionAndNoTransactionErrorHandlerConfiguredRoute")
                // must setup policy to indicate transacted route
                .policy(required)
                // send a request to http and get the response
                .to("http://localhost:" + port + "/sender")
                // convert the response to String so we can work with it and avoid streams only be readable once
                // as the http component will return data as a stream
                .convertBodyTo(String.class)
                // do a choice if the response is okay or not
                .choice()
                // do a xpath to compare if the status is NOT okay
                .when().xpath("/reply/status != 'ok'")
                // as this is based on an unit test we use mocks to verify how many times we did rollback
                .to("mock:JmsToHttpWithOnExceptionAndNoTransactionErrorHandlerConfiguredRoute")
                // response is not okay so force a rollback
                .rollback()
                .otherwise()
                // otherwise since its okay, the route ends and the response is sent back
                // to the original caller
                .end();

        // this is our http router
        from("jetty:http://localhost:" + port + "/sender").process(exchange -> {
            // first hit is always a error code 500 to force the caller to retry
            if (counter++ < 1) {
                // simulate http error 500
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                exchange.getMessage().setBody("Damn some internal server error");
                return;
            }

            String user = exchange.getIn().getHeader("user", String.class);
            if ("unknown".equals(user)) {
                // no page for a unknown user
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                exchange.getMessage().setBody("Page does not exist");
                return;
            } else if ("guest".equals(user)) {
                // not okay for guest user
                exchange.getMessage().setBody(nok);
                return;
            }

            exchange.getMessage().setBody(ok);
        });
    }

}
