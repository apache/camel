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
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import quickfix.Acceptor;
import quickfix.FieldNotFound;
import quickfix.FixVersions;
import quickfix.Initiator;
import quickfix.Session;
import quickfix.SessionFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.field.EmailThreadID;
import quickfix.field.EmailType;
import quickfix.field.MsgType;
import quickfix.field.Subject;
import quickfix.fix44.Email;
import quickfix.mina.ProtocolFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InOut exchanges between an in-process acceptor and initiator: the consumer replies only to the application messages
 * it is asked to answer, and the producer gets the reply that matches its correlation criteria.
 */
class QuickfixjInOutTest extends CamelTestSupport {

    private static final SessionID ACCEPTOR = new SessionID(FixVersions.BEGINSTRING_FIX44, "ACCEPTOR", "INITIATOR");
    private static final SessionID INITIATOR = new SessionID(FixVersions.BEGINSTRING_FIX44, "INITIATOR", "ACCEPTOR");
    private static final String CONSUMER = "quickfix:inout?sessionID=" + ACCEPTOR + "&exchangePattern=InOut";
    private static final String PRODUCER = "quickfix:inout?sessionID=" + INITIATOR;

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
        component.setConfigurations(Map.of("inout", configuration));
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
    void consumerRepliesOnlyToTheMessagesTheRouteAnswers() throws Exception {
        awaitLogon();
        MockEndpoint replies = getMockEndpoint("mock:replies");
        replies.expectedBodiesReceived("re: ping");

        // the route sets a null body for this one, so it must not be answered; the session keeps the order, so a
        // reply would arrive before the reply to the ping
        template.sendBody(PRODUCER, email("no reply"));
        template.sendBody(PRODUCER, email("ping"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void consumerRepliesWithTheReceivedMessageChangedInPlace() throws Exception {
        awaitLogon();
        MockEndpoint replies = getMockEndpoint("mock:replies");
        replies.expectedBodiesReceived("re: edit");

        template.sendBody(PRODUCER, email("edit"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void consumerReplyThatIsNotAFixMessageIsNotSent() throws Exception {
        awaitLogon();
        MockEndpoint requests = getMockEndpoint("mock:requests");
        requests.expectedBodiesReceived("map", "null", "ping");
        MockEndpoint replies = getMockEndpoint("mock:replies");
        replies.expectedBodiesReceived("re: ping");

        template.sendBody(PRODUCER, email("map"));
        template.sendBody(PRODUCER, email("null"));
        template.sendBody(PRODUCER, email("ping"));

        // each request is routed once: a reply that cannot be sent does not make the engine ask for it again
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void producerGetsTheReplyThatMatchesTheCriteria() throws Exception {
        awaitLogon();
        Exchange exchange = template.send(PRODUCER, ExchangePattern.InOut, e -> {
            e.getIn().setBody(email("ping"));
            // the reply is matched with the session ID as the replier sees it
            e.setProperty(QuickfixjProducer.CORRELATION_CRITERIA_KEY,
                    new MessagePredicate(ACCEPTOR, MsgType.EMAIL).withField(Subject.FIELD, "re: ping"));
            e.setProperty(QuickfixjProducer.CORRELATION_TIMEOUT_KEY, 10000L);
        });

        assertNull(exchange.getException());
        Email reply = assertInstanceOf(Email.class, exchange.getMessage().getBody());
        assertEquals("re: ping", reply.getSubject().getValue());
    }

    @Test
    void producerWithoutCriteriaFailsBeforeSending() throws Exception {
        awaitLogon();
        Exchange exchange = template.send(PRODUCER, ExchangePattern.InOut, e -> e.getIn().setBody(email("lost")));

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(e.getMessage().contains(QuickfixjProducer.CORRELATION_CRITERIA_KEY), e.getMessage());

        // the request was not sent, and the session still works
        MockEndpoint requests = getMockEndpoint("mock:requests");
        requests.expectedBodiesReceived("ping");
        template.sendBody(PRODUCER, email("ping"));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void producerBodyThatIsNotAFixMessageNamesItsType() throws Exception {
        awaitLogon();
        Exchange exchange = template.send(PRODUCER, e -> e.getIn().setBody(Map.of("Subject", "map")));

        InvalidPayloadException e = assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertTrue(e.getMessage().contains("quickfix.Message"), e.getMessage());
    }

    private void awaitLogon() throws InterruptedException {
        MockEndpoint logons = getMockEndpoint("mock:logon");
        logons.expectedMinimumMessageCount(2);
        logons.setResultWaitTime(10000);
        logons.assertIsSatisfied();
        logons.reset();
    }

    private static Email email(String subject) {
        return new Email(new EmailThreadID("ID"), new EmailType(EmailType.NEW), new Subject(subject));
    }

    private static String subject(Exchange exchange) throws FieldNotFound {
        return exchange.getIn().getBody(Email.class).getSubject().getValue();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the acceptor answers the pings; every event of the session reaches the InOut route
                from(CONSUMER).routeId("acceptor")
                        .choice()
                        .when(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.SessionLogon))
                        .to("mock:logon")
                        .when(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AppMessageReceived))
                        .setVariable("request", body())
                        .process(e -> e.getMessage().setBody(subject(e)))
                        .to("mock:requests")
                        .process(e -> {
                            String subject = e.getMessage().getBody(String.class);
                            switch (subject) {
                                case "ping" -> e.getMessage().setBody(email("re: ping"));
                                case "map" -> e.getMessage().setBody(Map.of("Subject", "re: map"));
                                // the received message changed in place is the reply
                                case "edit" -> {
                                    Email request = e.getVariable("request", Email.class);
                                    request.set(new Subject("re: edit"));
                                    e.getMessage().setBody(request);
                                }
                                // a null body: no reply
                                default -> e.getMessage().setBody(null);
                            }
                        });
                // the initiator, which sends the requests and receives the replies
                from(PRODUCER).routeId("initiator")
                        .choice()
                        .when(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.SessionLogon))
                        .to("mock:logon")
                        .when(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.AppMessageReceived))
                        .process(e -> e.getMessage().setBody(subject(e)))
                        .to("mock:replies");
            }
        };
    }
}
