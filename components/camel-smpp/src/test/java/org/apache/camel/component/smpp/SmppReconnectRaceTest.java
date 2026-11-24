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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.support.task.Task;
import org.apache.camel.support.task.TaskManagerRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
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
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.BindRequest;
import org.jsmpp.session.BroadcastSmResult;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.QueryBroadcastSmResult;
import org.jsmpp.session.QuerySmResult;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.SMPPServerSessionListener;
import org.jsmpp.session.SMPPSession;
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

/**
 * A lost session is always reconnected, also when it is lost while another reconnect still holds the reconnect lock,
 * and a reconnect attempt of a start that was stopped does not touch the session of the next start.
 */
class SmppReconnectRaceTest extends CamelTestSupport {

    @RegisterExtension
    static AvailablePortFinder.Port port = AvailablePortFinder.find();

    private final List<SMPPServerSession> serverSessions = new CopyOnWriteArrayList<>();
    private final AtomicInteger binds = new AtomicInteger();
    private final AtomicInteger submitted = new AtomicInteger();
    private final List<String> received = new CopyOnWriteArrayList<>();
    // the SMSC rejects the binds after this many
    private volatile int acceptedBinds = Integer.MAX_VALUE;
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
                if (binds.incrementAndGet() > acceptedBinds) {
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

    private static String uri(String extra) {
        return "smpp://smppclient@localhost:" + port.getPort()
               + "?password=password&initialReconnectDelay=100&reconnectDelay=100" + extra;
    }

    @Test
    void consumerReconnectsASessionLostBeforeTheReconnectReleasedItsLock() throws Exception {
        SmppEndpoint endpoint = context.getEndpoint(uri(""), SmppEndpoint.class);
        reconnectsASessionLostBeforeTheReconnectReleasedItsLock(endpoint, () -> endpoint.createConsumer(this::receive),
                "smpp-consumer-reconnect", service -> assertDelivered(serverSessions.get(2)));
    }

    @Test
    void producerReconnectsASessionLostBeforeTheReconnectReleasedItsLock() throws Exception {
        SmppEndpoint endpoint = context.getEndpoint(uri(""), SmppEndpoint.class);
        reconnectsASessionLostBeforeTheReconnectReleasedItsLock(endpoint, endpoint::createProducer,
                "smpp-producer-reconnect",
                // the reconnect thread sets session 3 just after the SMSC accepted it
                service -> await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertSubmitted((Producer) service)));
    }

    /**
     * The thread that reported the lost session 1 holds the reconnect lock while it waits for the reconnect task. The
     * task binds session 2, and the SMSC drops session 2 before that thread released the lock: the lost session 2 is
     * reconnected too.
     */
    private void reconnectsASessionLostBeforeTheReconnectReleasedItsLock(
            SmppEndpoint endpoint, ServiceSupplier factory, String taskName, ThrowingConsumer<Service> worksOnSession3)
            throws Exception {
        // runs on the thread that waited for the reconnect task, after the task completed and before that thread
        // releases the reconnect lock
        HookRegistry registry = new HookRegistry(
                context.getCamelContextExtension().getContextPlugin(TaskManagerRegistry.class), taskName);
        context.getCamelContextExtension().addContextPlugin(TaskManagerRegistry.class, registry);
        List<Session> bound = new CopyOnWriteArrayList<>();
        AtomicReference<Thread> reporterOfSession2 = new AtomicReference<>();
        endpoint.getConfiguration().setSessionStateListener((newState, oldState, source) -> {
            if (newState.isBound()) {
                bound.add(source);
            } else if (newState == SessionState.CLOSED && bound.size() == 2 && source == bound.get(1)) {
                reporterOfSession2.compareAndSet(null, Thread.currentThread());
            }
        });
        Service service = factory.get();
        try {
            service.start();
            await().atMost(10, TimeUnit.SECONDS).until(() -> serverSessions.size() == 1);

            registry.hook.set(() -> {
                // jsmpp reports the lost session 2 on the reader thread of session 2, which ends after the report
                serverSessions.get(1).close();
                await().atMost(10, TimeUnit.SECONDS).until(() -> reporterOfSession2.get() != null);
                await().atMost(10, TimeUnit.SECONDS).until(() -> !reporterOfSession2.get().isAlive());
            });

            // the SMSC drops session 1: the reconnect binds session 2, which the SMSC drops in the hook
            serverSessions.get(0).close();
            await().atMost(10, TimeUnit.SECONDS).until(() -> registry.hook.get() == null);

            // session 2 was reconnected as well
            await().atMost(10, TimeUnit.SECONDS).until(() -> serverSessions.size() == 3);
            worksOnSession3.accept(service);
        } finally {
            service.stop();
        }
    }

    @Test
    void consumerReconnectsANewSessionLostBeforeItIsSet() throws Exception {
        SmppEndpoint endpoint = context.getEndpoint(uri(""), SmppEndpoint.class);
        AtomicReference<Runnable> afterBind = new AtomicReference<>();
        SmppConsumer consumer = new SmppConsumer(endpoint, endpoint.getConfiguration(), this::receive) {
            @Override
            SMPPSession createSMPPSession() {
                return new HookedSession(afterBind);
            }
        };
        reconnectsANewSessionLostBeforeItIsSet(endpoint, consumer, afterBind, () -> assertDelivered(serverSessions.get(2)));
    }

    @Test
    void producerReconnectsANewSessionLostBeforeItIsSet() throws Exception {
        SmppEndpoint endpoint = context.getEndpoint(uri(""), SmppEndpoint.class);
        AtomicReference<Runnable> afterBind = new AtomicReference<>();
        SmppProducer producer = new SmppProducer(endpoint, endpoint.getConfiguration()) {
            @Override
            SMPPSession createSMPPSession() {
                return new HookedSession(afterBind);
            }
        };
        // the reconnect thread sets session 3 just after the SMSC accepted it
        reconnectsANewSessionLostBeforeItIsSet(endpoint, producer, afterBind,
                () -> await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertSubmitted(producer)));
    }

    /**
     * The reconnect attempt has bound session 2, and the SMSC drops it before the attempt sets it: jsmpp reports the
     * loss of session 2 on its reader thread while the thread that waits for the reconnect task still holds the lock.
     * Session 2 is not set, and the reconnect binds session 3.
     */
    private void reconnectsANewSessionLostBeforeItIsSet(
            SmppEndpoint endpoint, Service service, AtomicReference<Runnable> afterBind, ThrowingRunnable worksOnSession3)
            throws Exception {
        List<Session> bound = new CopyOnWriteArrayList<>();
        AtomicReference<Thread> reporterOfSession2 = new AtomicReference<>();
        endpoint.getConfiguration().setSessionStateListener((newState, oldState, source) -> {
            if (newState.isBound() && !bound.contains(source)) {
                bound.add(source);
            } else if (newState == SessionState.CLOSED && bound.size() == 2 && source == bound.get(1)) {
                reporterOfSession2.compareAndSet(null, Thread.currentThread());
            }
        });
        try {
            service.start();
            await().atMost(10, TimeUnit.SECONDS).until(() -> serverSessions.size() == 1);

            // runs on the reconnect thread, once connectAndBind of session 2 returned
            afterBind.set(() -> {
                serverSessions.get(1).close();
                await().atMost(10, TimeUnit.SECONDS).until(() -> reporterOfSession2.get() != null);
                await().atMost(10, TimeUnit.SECONDS).until(() -> !reporterOfSession2.get().isAlive());
            });
            serverSessions.get(0).close();

            await().atMost(10, TimeUnit.SECONDS).until(() -> serverSessions.size() == 3);
            worksOnSession3.run();
        } finally {
            service.stop();
        }
    }

    /** A session that runs a hook once, after a successful connectAndBind. */
    private static final class HookedSession extends SMPPSession {
        private final AtomicReference<Runnable> afterBind;

        HookedSession(AtomicReference<Runnable> afterBind) {
            this.afterBind = afterBind;
        }

        @Override
        public String connectAndBind(String host, int port, BindParameter bindParam) throws IOException {
            String systemId = super.connectAndBind(host, port, bindParam);
            Runnable hook = afterBind.getAndSet(null);
            if (hook != null) {
                hook.run();
            }
            return systemId;
        }
    }

    @Test
    void consumerStaleReconnectAttemptDoesNotCloseTheSessionOfTheNextStart() throws Exception {
        staleReconnectAttemptDoesNotCloseTheSessionOfTheNextStart(
                endpointUri -> context.getEndpoint(endpointUri).createConsumer(this::receive),
                service -> assertDelivered(serverSessions.get(1)));
    }

    @Test
    void producerStaleReconnectAttemptDoesNotCloseTheSessionOfTheNextStart() throws Exception {
        staleReconnectAttemptDoesNotCloseTheSessionOfTheNextStart(
                endpointUri -> context.getEndpoint(endpointUri).createProducer(),
                service -> assertSubmitted((Producer) service));
    }

    /**
     * A reconnect attempt waits in the connect (through an HTTP proxy that does not answer yet) while the consumer or
     * producer is stopped (the stop gives up waiting for it after 1 s) and started again. The attempt then fails, and
     * must leave the session of the new start alone.
     */
    private void staleReconnectAttemptDoesNotCloseTheSessionOfTheNextStart(
            ServiceFactory factory, ThrowingConsumer<Service> worksOnTheNewSession)
            throws Exception {
        CountDownLatch releaseSlowConnect = new CountDownLatch(1);
        AtomicInteger proxyConnects = new AtomicInteger();
        try (ServerSocket proxy = new ServerSocket(0)) {
            Thread proxyThread = new Thread(() -> runProxy(proxy, proxyConnects, releaseSlowConnect), "test-proxy");
            proxyThread.setDaemon(true);
            proxyThread.start();

            Service service = factory.create(uri("&httpProxyHost=localhost&httpProxyPort=" + proxy.getLocalPort()));
            try {
                service.start();
                await().atMost(10, TimeUnit.SECONDS).until(() -> serverSessions.size() == 1);

                // the SMSC drops session 1; the reconnect attempt goes through the proxy, which does not answer yet
                serverSessions.get(0).close();
                await().atMost(10, TimeUnit.SECONDS).until(() -> proxyConnects.get() == 2);

                service.stop();
                service.start();
                await().atMost(10, TimeUnit.SECONDS).until(() -> serverSessions.size() == 2);

                // the proxy fails the attempt of the stopped start
                releaseSlowConnect.countDown();
                await().atMost(10, TimeUnit.SECONDS).until(() -> threadsIn("tryCreateSession") == 0);

                worksOnTheNewSession.accept(service);
                assertEquals(2, binds.get());
            } finally {
                releaseSlowConnect.countDown();
                service.stop();
            }
        }
    }

    @Test
    void consumerReconnectEndsAfterMaxReconnectWhenTheRejectedBindsAreReportedOnAnotherThread() throws Exception {
        SmppEndpoint endpoint = context.getEndpoint(uri("&maxReconnect=2"), SmppEndpoint.class);
        reconnectEndsAfterMaxReconnect(new SmppConsumer(endpoint, endpoint.getConfiguration(), this::receive) {
            @Override
            SMPPSession createSMPPSession() {
                return new CloseOnAnotherThreadSession();
            }
        });
    }

    @Test
    void producerReconnectEndsAfterMaxReconnectWhenTheRejectedBindsAreReportedOnAnotherThread() throws Exception {
        SmppEndpoint endpoint = context.getEndpoint(uri("&maxReconnect=2"), SmppEndpoint.class);
        reconnectEndsAfterMaxReconnect(new SmppProducer(endpoint, endpoint.getConfiguration()) {
            @Override
            SMPPSession createSMPPSession() {
                return new CloseOnAnotherThreadSession();
            }
        });
    }

    /**
     * The SMSC drops session 1 and rejects every bind after it. jsmpp reports the close of a rejected bind on its
     * reader thread when the SMSC closes the connection after its bind_resp (the test session does so on another
     * thread): that report must not ask for another reconnect, so the reconnect gives up after its 2 attempts.
     */
    private void reconnectEndsAfterMaxReconnect(Service service) throws Exception {
        acceptedBinds = 1;
        try {
            service.start();
            await().atMost(10, TimeUnit.SECONDS).until(() -> serverSessions.size() == 1);

            serverSessions.get(0).close();
            await().atMost(10, TimeUnit.SECONDS).until(() -> binds.get() == 3);
            // a reconnect started again would bind every 100 ms
            await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).until(() -> binds.get() == 3);
        } finally {
            service.stop();
        }
    }

    /**
     * A session that reports the close of a failed bind on another thread, before connectAndBind throws, as jsmpp does
     * when its reader thread sees the end of the connection first.
     */
    private static final class CloseOnAnotherThreadSession extends SMPPSession {
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile boolean binding;

        @Override
        public String connectAndBind(String host, int port, BindParameter bindParam) throws IOException {
            binding = true;
            try {
                return super.connectAndBind(host, port, bindParam);
            } finally {
                binding = false;
            }
        }

        @Override
        public void close() {
            if (binding && closed.compareAndSet(false, true)) {
                Thread reporter = new Thread(super::close, "test-reader");
                reporter.start();
                try {
                    reporter.join(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                super.close();
            }
        }
    }

    private void receive(Exchange exchange) {
        received.add(exchange.getIn().getBody(String.class));
    }

    private void assertDelivered(SMPPServerSession serverSession) throws Exception {
        received.clear();
        // returns once the consumer answered the deliver_sm, which it does after it processed the message
        serverSession.deliverShortMessage("CMT", TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, "1616",
                TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, "1717", new ESMClass(), (byte) 0, (byte) 1,
                new RegisteredDelivery(0), DataCodings.ZERO, "hello".getBytes(StandardCharsets.US_ASCII));
        assertEquals(List.of("hello"), received);
    }

    private void assertSubmitted(Producer producer) throws Exception {
        int before = submitted.get();
        Exchange exchange = producer.getEndpoint().createExchange();
        exchange.getIn().setBody("hello");
        producer.process(exchange);
        assertEquals(before + 1, submitted.get());
    }

    private static long threadsIn(String method) {
        return Thread.getAllStackTraces().values().stream()
                .filter(stack -> Arrays.stream(stack)
                        .anyMatch(frame -> (frame.getClassName().equals(SmppConsumer.class.getName())
                                || frame.getClassName().equals(SmppProducer.class.getName()))
                                && frame.getMethodName().equals(method)))
                .count();
    }

    /** A minimal HTTP CONNECT proxy to the SMSC; it answers the second CONNECT only when released, with a 502. */
    private static void runProxy(ServerSocket proxy, AtomicInteger connects, CountDownLatch releaseSlow) {
        try {
            while (true) {
                Socket client = proxy.accept();
                int n = connects.incrementAndGet();
                Thread handler = new Thread(() -> proxyConnection(client, n, releaseSlow), "test-proxy-" + n);
                handler.setDaemon(true);
                handler.start();
            }
        } catch (IOException e) {
            // the proxy is closed
        }
    }

    private static void proxyConnection(Socket client, int n, CountDownLatch releaseSlow) {
        try {
            BufferedReader reader
                    = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            String line;
            do {
                // the CONNECT request and its headers
                line = reader.readLine();
            } while (line != null && !line.isEmpty());
            OutputStream toClient = client.getOutputStream();
            if (n == 2) {
                releaseSlow.await(30, TimeUnit.SECONDS);
                toClient.write("HTTP/1.0 502 Bad Gateway\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                client.close();
                return;
            }
            Socket smscSide = new Socket("localhost", port.getPort());
            toClient.write("HTTP/1.0 200 Connection established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            pipe(client.getInputStream(), smscSide.getOutputStream(), client, smscSide);
            pipe(smscSide.getInputStream(), toClient, client, smscSide);
        } catch (Exception e) {
            // the connection ended
        }
    }

    private static void pipe(InputStream in, OutputStream out, Socket a, Socket b) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[4096];
            try {
                int r;
                while ((r = in.read(buf)) >= 0) {
                    out.write(buf, 0, r);
                    out.flush();
                }
            } catch (IOException e) {
                // the connection ended
            } finally {
                closeQuietly(a);
                closeQuietly(b);
            }
        }, "test-proxy-pipe");
        t.setDaemon(true);
        t.start();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    interface ServiceSupplier {
        Service get() throws Exception;
    }

    @FunctionalInterface
    interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    @FunctionalInterface
    interface ServiceFactory {
        Service create(String uri) throws Exception;
    }

    /** Runs a hook once, on the thread that removes the given reconnect task after it waited for it. */
    static final class HookRegistry implements TaskManagerRegistry {
        final TaskManagerRegistry delegate;
        final String taskName;
        final AtomicReference<Runnable> hook = new AtomicReference<>();

        HookRegistry(TaskManagerRegistry delegate, String taskName) {
            this.delegate = delegate;
            this.taskName = taskName;
        }

        @Override
        public void addTask(Task task) {
            delegate.addTask(task);
        }

        @Override
        public void removeTask(Task task) {
            if (taskName.equals(task.getName())) {
                Runnable h = hook.get();
                if (h != null) {
                    h.run();
                    hook.set(null);
                }
            }
            delegate.removeTask(task);
        }

        @Override
        public int getSize() {
            return delegate.getSize();
        }

        @Override
        public Set<Task> getTasks() {
            return delegate.getTasks();
        }

        @Override
        public void start() {
            delegate.start();
        }

        @Override
        public void stop() {
            delegate.stop();
        }
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
