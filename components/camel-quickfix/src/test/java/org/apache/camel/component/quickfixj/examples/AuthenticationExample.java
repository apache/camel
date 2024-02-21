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
package org.apache.camel.component.quickfixj.examples;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.quickfixj.QuickfixjEndpoint;
import org.apache.camel.component.quickfixj.QuickfixjEventCategory;
import org.apache.camel.component.quickfixj.examples.util.CountDownLatchDecrementer;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.RejectLogon;
import quickfix.field.MsgType;
import quickfix.field.RawData;
import quickfix.field.RawDataLength;

/**
 * This example demonstrates several features of the QuickFIX/J component. It uses QFJ session events to synchronize
 * application behavior (e.g., Session logon).
 */
public class AuthenticationExample {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationExample.class);

    public static void main(String[] args) throws Exception {
        new AuthenticationExample().run();
    }

    public void run() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();

        final CountDownLatch logoutLatch = new CountDownLatch(1);

        RouteBuilder routes = new RouteBuilder() {
            @Override
            public void configure() {
                // Modify the outgoing logon message to add a password
                // The modified message will be sent from the FIX engine when the message exchange completes
                from("quickfix:examples/inprocess.qf.cfg?sessionID=FIX.4.2:TRADER->MARKET").filter(PredicateBuilder.and(
                        header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AdminMessageSent),
                        header(QuickfixjEndpoint.MESSAGE_TYPE_KEY).isEqualTo(MsgType.LOGON)))
                        .bean(new CredentialInjector("PASSWORD"));

                // Release latch when the trader received a logout message
                from("quickfix:examples/inprocess.qf.cfg?sessionID=FIX.4.2:TRADER->MARKET")
                        .filter(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.SessionLogoff))
                        .bean(new CountDownLatchDecrementer("logout", logoutLatch));

                // Reject all logons on market side
                // Demonstrates how to validate logons
                from("quickfix:examples/inprocess.qf.cfg?sessionID=FIX.4.2:MARKET->TRADER").filter(PredicateBuilder.and(
                        header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AdminMessageReceived),
                        header(QuickfixjEndpoint.MESSAGE_TYPE_KEY).isEqualTo(MsgType.LOGON))).bean(new LogonAuthenticator());
            }
        };

        context.addRoutes(routes);

        LOG.info("Starting Camel context");
        context.start();

        if (!logoutLatch.await(5L, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Logout was not received");
        }

        context.stop();

        LOG.info("Example complete");
    }

    public static class LogonAuthenticator {
        public void authenticate(Exchange exchange) throws RejectLogon, CamelExchangeException, FieldNotFound {
            LOG.info("Acceptor is rejecting logon for {}", exchange.getIn().getHeader(QuickfixjEndpoint.SESSION_ID_KEY));
            Message message = exchange.getIn().getMandatoryBody(Message.class);
            if (message.isSetField(RawData.FIELD)) {
                LOG.info("Invalid password: {}", message.getString(RawData.FIELD));
            }
            throw new RejectLogon("Rejecting logon for test purposes");
        }
    }

    public static class CredentialInjector {
        private final String password;

        public CredentialInjector(String password) {
            this.password = password;
        }

        public void inject(Exchange exchange) throws CamelExchangeException {
            LOG.info("Injecting password into outgoing logon message");
            Message message = exchange.getIn().getMandatoryBody(Message.class);
            message.setString(RawData.FIELD, password);
            message.setInt(RawDataLength.FIELD, password.length());
        }
    }
}
