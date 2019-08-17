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
import org.apache.camel.Processor;
import org.apache.camel.test.AvailablePortFinder;

/**
 * Route that listen on a JMS queue and send a request/reply over http
 * before returning a response. Is transacted.
 * <p/>
 * Notice we use the SpringRouteBuilder that supports transacted
 * error handler.
 */
public class JmsToHttpWithRollbackRoute extends JmsToHttpRoute {

    @Override
    public void configure() throws Exception {
        port = AvailablePortFinder.getNextAvailable();

        // configure a global transacted error handler
        errorHandler(transactionErrorHandler(required));

        from(data)
            // must setup policy for each route due CAMEL-1475 bug
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
                    .to("mock:rollback")
                    // response is not okay so force a rollback
                    .rollback()
                .otherwise()
                // otherwise since its okay, the route ends and the response is sent back
                // to the original caller
            .end();

        // this is our http route that will fail the first 2 attempts
        // before it sends an ok response
        from("jetty:http://localhost:" + port + "/sender").process(new Processor() {
            public void process(Exchange exchange) throws Exception {
                if (counter++ < 2) {
                    exchange.getOut().setBody(nok);
                } else {
                    exchange.getOut().setBody(ok);
                }
            }
        });
    }

}