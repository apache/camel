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
package org.apache.camel.component.quickfixj;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import quickfix.Acceptor;
import quickfix.FixVersions;
import quickfix.Initiator;
import quickfix.Session;
import quickfix.SessionFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.field.EmailThreadID;
import quickfix.field.EmailType;
import quickfix.field.Subject;
import quickfix.fix44.Email;
import quickfix.mina.ProtocolFactory;

/**
 * Removing one of the routes that consume from a quickfix endpoint must not take the other consumers of the endpoint
 * off the FIX session.
 */
class QuickfixjRemoveRouteTest extends CamelTestSupport {

    private static final SessionID ACCEPTOR = new SessionID(FixVersions.BEGINSTRING_FIX44, "ACCEPTOR", "INITIATOR");
    private static final SessionID INITIATOR = new SessionID(FixVersions.BEGINSTRING_FIX44, "INITIATOR", "ACCEPTOR");
    private static final String CONSUMER = "quickfix:remove-route?sessionID=" + ACCEPTOR;
    private static final String PRODUCER = "quickfix:remove-route?sessionID=" + INITIATOR;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // the values are text, as in a settings file; the vm pipe "port" is a name inside the JVM, not a socket
        long port = ThreadLocalRandom.current().nextInt(20000, 30000);

        Map<Object, Object> defaults = new HashMap<>();
        defaults.put(Acceptor.SETTING_SOCKET_ACCEPT_PROTOCOL, ProtocolFactory.getTypeString(ProtocolFactory.VM_PIPE));
        defaults.put(Initiator.SETTING_SOCKET_CONNECT_PROTOCOL, ProtocolFactory.getTypeString(ProtocolFactory.VM_PIPE));
        defaults.put(Session.SETTING_USE_DATA_DICTIONARY, "N");
        defaults.put(Session.SETTING_NON_STOP_SESSION, "Y");

        QuickfixjConfiguration configuration = new QuickfixjConfiguration();
        configuration.setDefaultSettings(defaults);
        configuration.addSessionSetting(ACCEPTOR, session(ACCEPTOR, SessionFactory.ACCEPTOR_CONNECTION_TYPE,
                Acceptor.SETTING_SOCKET_ACCEPT_PORT, port));
        configuration.addSessionSetting(INITIATOR, session(INITIATOR, SessionFactory.INITIATOR_CONNECTION_TYPE,
                Initiator.SETTING_SOCKET_CONNECT_PORT, port));

        QuickfixjComponent component = new QuickfixjComponent();
        component.setConfigurations(Map.of("remove-route", configuration));
        context.addComponent("quickfix", component);
        return context;
    }

    private static Map<Object, Object> session(SessionID id, String connectionType, String portKey, long port) {
        Map<Object, Object> settings = new HashMap<>();
        settings.put(SessionSettings.BEGINSTRING, id.getBeginString());
        settings.put(SessionSettings.SENDERCOMPID, id.getSenderCompID());
        settings.put(SessionSettings.TARGETCOMPID, id.getTargetCompID());
        settings.put(SessionFactory.SETTING_CONNECTION_TYPE, connectionType);
        settings.put(portKey, String.valueOf(port));
        settings.put(Initiator.SETTING_RECONNECT_INTERVAL, "1");
        settings.put(Session.SETTING_HEARTBTINT, "30");
        return settings;
    }

    @Test
    void removingOneOfTwoConsumerRoutesKeepsTheOtherReceiving() throws Exception {
        awaitLogon();
        MockEndpoint a = getMockEndpoint("mock:a");
        MockEndpoint b = getMockEndpoint("mock:b");
        a.expectedMessageCount(1);
        b.expectedMessageCount(1);
        template.sendBody(PRODUCER, email("first"));
        MockEndpoint.assertIsSatisfied(context);

        context.getRouteController().stopRoute("b");
        context.removeRoute("b");

        a.reset();
        a.expectedMessageCount(1);
        template.sendBody(PRODUCER, email("second"));
        a.assertIsSatisfied();
    }

    @Test
    void stoppingOneOfTwoConsumerRoutesKeepsTheOtherReceiving() throws Exception {
        awaitLogon();
        context.getRouteController().stopRoute("b");

        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(1);
        template.sendBody(PRODUCER, email("after stop"));
        a.assertIsSatisfied();
    }

    @Test
    void removingRoutesThatNeverStartedKeepsTheOthersReceiving() throws Exception {
        awaitLogon();
        // standby routes on the same endpoint, removed without being started: Camel stops their consumers, which never
        // took a reference to the engine, so they must not release one (enough of them to release every reference)
        for (int i = 0; i < 8; i++) {
            String id = "standby" + i;
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(CONSUMER).routeId(id).autoStartup(false).to("mock:standby");
                }
            });
        }
        for (int i = 0; i < 8; i++) {
            context.removeRoute("standby" + i);
        }

        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(1);
        template.sendBody(PRODUCER, email("after removing the standby routes"));
        a.assertIsSatisfied();
    }

    private void awaitLogon() throws InterruptedException {
        MockEndpoint logons = getMockEndpoint("mock:logon");
        logons.expectedMinimumMessageCount(2);
        logons.setResultWaitTime(10000);
        logons.assertIsSatisfied();
    }

    private static Email email(String subject) {
        return new Email(new EmailThreadID("ID"), new EmailType(EmailType.NEW), new Subject(subject));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(CONSUMER).routeId("a")
                        .choice()
                        .when(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AppMessageReceived))
                        .to("mock:a")
                        .when(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.SessionLogon))
                        .to("mock:logon");
                from(CONSUMER).routeId("b")
                        .filter(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY)
                                .isEqualTo(QuickfixjEventCategory.AppMessageReceived))
                        .to("mock:b");
                // the logon of the initiator, which sends the messages
                from(PRODUCER).routeId("initiator")
                        .filter(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.SessionLogon))
                        .to("mock:logon");
            }
        };
    }
}
