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
package org.apache.camel.component.smpp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.jsmpp.PDUStringException;
import org.jsmpp.SMPPConstant;
import org.jsmpp.bean.BroadcastSm;
import org.jsmpp.bean.CancelBroadcastSm;
import org.jsmpp.bean.CancelSm;
import org.jsmpp.bean.DataCodings;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.QueryBroadcastSm;
import org.jsmpp.bean.QuerySm;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.ReplaceSm;
import org.jsmpp.bean.SubmitMulti;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.BindRequest;
import org.jsmpp.session.BroadcastSmResult;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.QueryBroadcastSmResult;
import org.jsmpp.session.QuerySmResult;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.SMPPServerSessionListener;
import org.jsmpp.session.ServerMessageReceiverListener;
import org.jsmpp.session.Session;
import org.jsmpp.session.SubmitMultiResult;
import org.jsmpp.session.SubmitSmResult;
import org.jsmpp.util.MessageId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A bind that the SMSC rejects while the consumer or producer starts (or while a lazy producer creates its session)
 * fails that start (or that exchange) at once, as a refused connection does. It must not run the reconnect loop on the
 * starting thread, where the start never returned while the SMSC kept rejecting and a stop from another thread waited
 * for it.
 */
class SmppBindRejectedTest extends CamelTestSupport {

    @RegisterExtension
    static AvailablePortFinder.Port port = AvailablePortFinder.find();

    private final List<SMPPServerSession> serverSessions = new CopyOnWriteArrayList<>();
    private final AtomicInteger binds = new AtomicInteger();
    private final AtomicInteger submitted = new AtomicInteger();
    private volatile boolean reject = true;
    private SMPPServerSessionListener smsc;

    @BeforeEach
    void startSmsc() throws IOException {
        smsc = new SMPPServerSessionListener(port.getPort());
        Thread acceptor = new Thread(this::acceptBinds, "smsc-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @AfterEach
    void stopSmsc() throws IOException {
        smsc.close();
        serverSessions.forEach(SMPPServerSession::close);
    }

    private void acceptBinds() {
        try {
            while (true) {
                SMPPServerSession session = smsc.accept();
                session.setMessageReceiverListener(new AcceptSubmitSm());
                BindRequest bind = session.waitForBind(5000);
                binds.incrementAndGet();
                if (reject) {
                    // negative bind_resp, as for wrong credentials or a throttled ESME; the ESME closes the connection
                    bind.reject(SMPPConstant.STAT_ESME_RBINDFAIL);
                } else {
                    bind.accept("smsc");
                    serverSessions.add(session);
                }
            }
        } catch (Exception e) {
            // the SMSC is closed
        }
    }

    private static String uri() {
        // a reconnect loop would bind again every 100 ms
        return "smpp://smppclient@localhost:" + port.getPort()
               + "?password=password&initialReconnectDelay=100&reconnectDelay=100";
    }

    @Test
    void consumerStartFailsAtOnceWhenTheSmscRejectsTheBind() throws Exception {
        List<String> received = new CopyOnWriteArrayList<>();
        Consumer consumer = context.getEndpoint(uri())
                .createConsumer(exchange -> received.add(exchange.getIn().getBody(String.class)));
        try {
            startFailsAtOnce(consumer);

            // the SMSC accepts binds again: a new start binds, and the consumer receives
            reject = false;
            consumer.start();
            await().atMost(10, TimeUnit.SECONDS).until(() -> serverSessions.size() == 1);
            // returns once the consumer answered the deliver_sm, which it does after it processed the message
            serverSessions.get(0).deliverShortMessage("CMT", TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, "1616",
                    TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, "1717", new ESMClass(), (byte) 0, (byte) 1,
                    new RegisteredDelivery(0), DataCodings.ZERO, "hello".getBytes(StandardCharsets.US_ASCII));
            assertEquals(List.of("hello"), received);
        } finally {
            // without the fix the start still waits in the reconnect: let it bind so that it ends
            reject = false;
            consumer.stop();
        }
    }

    @Test
    void producerStartFailsAtOnceWhenTheSmscRejectsTheBind() throws Exception {
        Producer producer = context.getEndpoint(uri()).createProducer();
        try {
            startFailsAtOnce(producer);

            // the SMSC accepts binds again: a new start binds, and the producer sends
            reject = false;
            producer.start();
            producer.process(newExchange());
            assertEquals(1, submitted.get());
        } finally {
            reject = false;
            producer.stop();
        }
    }

    @Test
    void lazyProducerFailsTheExchangeAtOnceWhenTheSmscRejectsTheBind() throws Exception {
        Producer producer = context.getEndpoint(uri() + "&lazySessionCreation=true").createProducer();
        producer.start();
        try {
            Exchange exchange = newExchange();
            CompletableFuture<Void> send = CompletableFuture.runAsync(() -> {
                try {
                    producer.process(exchange);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });

            // the route thread gets the bind failure; it does not wait for a reconnect
            ExecutionException failure = assertThrows(ExecutionException.class, () -> send.get(5, TimeUnit.SECONDS));
            assertNotNull(ObjectHelper.getException(IOException.class, failure));
            await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).until(() -> binds.get() == 1);

            // the SMSC accepts binds again: the next exchange creates the session and is sent
            reject = false;
            producer.process(newExchange());
            assertEquals(1, submitted.get());
        } finally {
            reject = false;
            producer.stop();
        }
    }

    private void startFailsAtOnce(Service service) throws Exception {
        CompletableFuture<Void> start = CompletableFuture.runAsync(service::start);

        // the start fails with the bind error, and the bind is not tried again
        ExecutionException failure = assertThrows(ExecutionException.class, () -> start.get(5, TimeUnit.SECONDS));
        assertNotNull(ObjectHelper.getException(IOException.class, failure));
        await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).until(() -> binds.get() == 1);

        // a stop from another thread does not wait for anything
        CompletableFuture.runAsync(service::stop).get(5, TimeUnit.SECONDS);
    }

    private Exchange newExchange() {
        Exchange exchange = context.getEndpoint(uri()).createExchange();
        exchange.getIn().setBody("hello");
        return exchange;
    }

    /** Accepts every submit_sm; the tests send nothing else. */
    private final class AcceptSubmitSm implements ServerMessageReceiverListener {

        @Override
        public SubmitSmResult onAcceptSubmitSm(SubmitSm submitSm, SMPPServerSession source)
                throws ProcessRequestException {
            submitted.incrementAndGet();
            try {
                return new SubmitSmResult(new MessageId("1"), new OptionalParameter[0]);
            } catch (PDUStringException e) {
                throw new ProcessRequestException(e.getMessage(), SMPPConstant.STAT_ESME_RSYSERR, e);
            }
        }

        @Override
        public SubmitMultiResult onAcceptSubmitMulti(SubmitMulti submitMulti, SMPPServerSession source)
                throws ProcessRequestException {
            throw notSupported();
        }

        @Override
        public QuerySmResult onAcceptQuerySm(QuerySm querySm, SMPPServerSession source) throws ProcessRequestException {
            throw notSupported();
        }

        @Override
        public void onAcceptReplaceSm(ReplaceSm replaceSm, SMPPServerSession source) throws ProcessRequestException {
            throw notSupported();
        }

        @Override
        public void onAcceptCancelSm(CancelSm cancelSm, SMPPServerSession source) throws ProcessRequestException {
            throw notSupported();
        }

        @Override
        public BroadcastSmResult onAcceptBroadcastSm(BroadcastSm broadcastSm, SMPPServerSession source)
                throws ProcessRequestException {
            throw notSupported();
        }

        @Override
        public void onAcceptCancelBroadcastSm(CancelBroadcastSm cancelBroadcastSm, SMPPServerSession source)
                throws ProcessRequestException {
            throw notSupported();
        }

        @Override
        public QueryBroadcastSmResult onAcceptQueryBroadcastSm(QueryBroadcastSm queryBroadcastSm, SMPPServerSession source)
                throws ProcessRequestException {
            throw notSupported();
        }

        @Override
        public DataSmResult onAcceptDataSm(DataSm dataSm, Session source) throws ProcessRequestException {
            throw notSupported();
        }

        private ProcessRequestException notSupported() {
            return new ProcessRequestException("not supported by the test SMSC", SMPPConstant.STAT_ESME_RINVCMDID);
        }
    }
}
