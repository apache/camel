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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.task.BackgroundTask;
import org.apache.camel.util.ObjectHelper;
import org.jsmpp.DefaultPDUReader;
import org.jsmpp.DefaultPDUSender;
import org.jsmpp.SynchronizedPDUSender;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.jsmpp.session.SessionStateListener;
import org.jsmpp.util.DefaultComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.smpp.SmppUtils.createExecutor;
import static org.apache.camel.component.smpp.SmppUtils.isServiceStopping;
import static org.apache.camel.component.smpp.SmppUtils.isSessionClosed;
import static org.apache.camel.component.smpp.SmppUtils.isSessionLost;
import static org.apache.camel.component.smpp.SmppUtils.newReconnectTask;
import static org.apache.camel.component.smpp.SmppUtils.shutdownReconnectService;

/**
 * An implementation of @{link Producer} which use the SMPP protocol
 */
public class SmppProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SmppProducer.class);
    private static final String RECONNECT_TASK_NAME = "smpp-producer-reconnect";

    private final SmppConfiguration configuration;
    private final SessionStateListener internalSessionStateListener;
    private final ReentrantLock connectLock = new ReentrantLock();
    // the session whose connectAndBind runs on this thread: jsmpp closes a session whose bind fails on the thread that
    // called connectAndBind, and calls the session state listeners there before connectAndBind throws
    private final ThreadLocal<Session> bindingSession = new ThreadLocal<>();
    // created again by a start after a stop, as the stop shuts it down
    private volatile ScheduledExecutorService reconnectService;

    private volatile SMPPSession session;
    // the reconnect task that the thread which reported the lost session waits for, so that a stop can release it
    private volatile BackgroundTask reconnectTask;
    // held by the thread that runs the reconnect task; connectLock is held only while a session is created and set
    private final ReentrantLock reconnectLock = new ReentrantLock();
    // set by the loss of the session in use before it tries reconnectLock, and looked at again by the thread that holds
    // when it is done: a session lost while another thread holds the lock is reconnected too
    private final AtomicBoolean reconnectRequested = new AtomicBoolean();
    // the session and the generation are changed together under this lock, so that a reconnect attempt, a lost
    // session and a stop do not replace or close each other's session
    private final Object sessionLock = new Object();
    // incremented by every stop: a reconnect belongs to the start it was requested in, ends when that start is
    // stopped, and never touches the session of a later start
    private final AtomicInteger generation = new AtomicInteger();

    public SmppProducer(SmppEndpoint endpoint, SmppConfiguration config) {
        super(endpoint);

        this.reconnectService = createExecutor(this, endpoint, RECONNECT_TASK_NAME);

        this.configuration = config;
        this.internalSessionStateListener = (newState, oldState, source) -> {
            if (configuration.getSessionStateListener() != null) {
                configuration.getSessionStateListener().onStateChange(newState, oldState, source);
            }

            if (newState.equals(SessionState.UNBOUND) || newState.equals(SessionState.CLOSED)) {
                if (source != null && source == bindingSession.get()) {
                    // the bind of this session failed on this thread, and connectAndBind throws to its caller (the
                    // start, a reconnect attempt or a lazy session creation), which handles it. Reconnecting here
                    // would run the whole reconnect on that thread: inside start(), under the lock stop() needs too
                    LOG.debug("The bind to {} failed", getEndpoint().getConnectionString());
                    return;
                }
                LOG.warn(newState.equals(SessionState.UNBOUND)
                        ? "Session to {} was unbound - trying to reconnect" : "Lost connection to: {} - trying to reconnect...",
                        getEndpoint().getConnectionString());
                if (closeLostSession(source)) {
                    reconnect();
                }
            }
        };
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        restartReconnectService();

        if (!getConfiguration().isLazySessionCreation()) {
            if (connectLock.tryLock()) {
                try {
                    setNewSession(createSession());
                } finally {
                    connectLock.unlock();
                }
            } else {
                LOG.warn("Thread {} could not acquire a lock for creating the session during producer start",
                        Thread.currentThread().threadId());
            }
        }
    }

    private SMPPSession createSession() throws Exception {
        LOG.debug("Connecting to: {}...", getEndpoint().getConnectionString());

        SMPPSession session = createSMPPSession();
        session.setEnquireLinkTimer(this.configuration.getEnquireLinkTimer());
        session.setTransactionTimer(this.configuration.getTransactionTimer());
        session.setPduProcessorDegree(this.configuration.getPduProcessorDegree());
        session.setQueueCapacity(this.configuration.getPduProcessorQueueCapacity());
        session.addSessionStateListener(internalSessionStateListener);
        BindType bindType = BindType.BIND_TX;
        if (ObjectHelper.isNotEmpty(this.configuration.getMessageReceiverRouteId())) {
            session.setMessageReceiverListener(new MessageReceiverListenerImpl(
                    getEndpoint(),
                    this.configuration.getMessageReceiverRouteId()));
            bindType = BindType.BIND_TRX;
        }
        bindingSession.set(session);
        try {
            session.connectAndBind(
                    this.configuration.getHost(),
                    this.configuration.getPort(),
                    new BindParameter(
                            bindType,
                            this.configuration.getSystemId(),
                            this.configuration.getPassword(),
                            this.configuration.getSystemType(),
                            TypeOfNumber.valueOf(configuration.getTypeOfNumber()),
                            NumberingPlanIndicator.valueOf(configuration.getNumberingPlanIndicator()),
                            "",
                            configuration.getInterfaceVersionByte()));
        } finally {
            bindingSession.remove();
        }

        LOG.info("Connected to: {}", getEndpoint().getConnectionString());

        return session;
    }

    /**
     * Factory method to easily instantiate a mock SMPPSession
     *
     * @return the SMPPSession
     */
    SMPPSession createSMPPSession() {
        return new SMPPSession(
                new SynchronizedPDUSender(
                        new DefaultPDUSender(
                                new DefaultComposer())),
                new DefaultPDUReader(), SmppConnectionFactory
                        .getInstance(configuration));
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (session == null) {
            if (this.configuration.isLazySessionCreation()) {
                if (connectLock.tryLock()) {
                    try {
                        if (session == null) {
                            if (this.configuration.isAllowSystemIdAndPasswordFromHeaders()) {
                                // set the system id and password with which we will try to connect to the SMSC
                                Message in = exchange.getIn();
                                String systemId = in.getHeader(SmppConstants.SYSTEM_ID, String.class);
                                String password = in.getHeader(SmppConstants.PASSWORD, String.class);
                                if (systemId != null && password != null) {
                                    LOG.info("using the system id '{}' to connect to the SMSC...", systemId);
                                    this.configuration.setSystemId(systemId);
                                    this.configuration.setPassword(password);
                                }
                            }
                            setNewSession(createSession());
                        }
                    } finally {
                        connectLock.unlock();
                    }
                } else {
                    LOG.warn("Thread {} could not acquire a lock for creating the session during lazy initialization",
                            Thread.currentThread().threadId());
                }
            }
        }

        // only possible by trying to reconnect
        if (this.session == null) {
            throw new IOException("Lost connection to " + getEndpoint().getConnectionString() + " and yet not reconnected");
        }

        SmppCommand command = getEndpoint().getBinding().createSmppCommand(session, exchange);
        command.execute(exchange);
    }

    /**
     * A stop shuts down the reconnect service. When this instance is started again in place (such as by suspendRoute
     * and resumeRoute, which stop and start a service that is not Suspendable), a lost session would otherwise schedule
     * its reconnect task on the shut down service, where it never runs.
     */
    private void restartReconnectService() {
        if (reconnectService.isShutdown()) {
            reconnectService = createExecutor(this, getEndpoint(), RECONNECT_TASK_NAME);
        }
    }

    @Override
    protected void doStop() throws Exception {
        synchronized (sessionLock) {
            generation.incrementAndGet();
        }
        reconnectRequested.set(false);
        shutdownReconnectService(reconnectService);
        // the shut down reconnect service no longer runs the reconnect task, so nothing else would release the thread
        // that waits for it
        BackgroundTask task = reconnectTask;
        if (task != null) {
            task.cancel(false);
        }

        LOG.debug("Disconnecting from: {}...", getEndpoint().getConnectionString());

        super.doStop();
        closeSession();

        LOG.info("Disconnected from: {}", getEndpoint().getConnectionString());
    }

    private void closeSession() {
        SMPPSession current;
        synchronized (sessionLock) {
            current = session;
            session = null;
        }
        if (current != null) {
            current.removeSessionStateListener(this.internalSessionStateListener);
            current.unbindAndClose();
        }
    }

    /**
     * Closes the session that was lost, and returns whether it was the session in use: only that loss asks for a
     * reconnect. The field is cleared only when it holds that session: it may hold the session of a later reconnect or
     * start, or none when the lost session was never set (its bind failed, which jsmpp reports on its reader thread
     * when the SMSC closes the connection after its bind_resp, or the SMSC dropped it before it was set). A state
     * change with no source is taken as the loss of the session in use.
     */
    private boolean closeLostSession(Session source) {
        Session lost;
        boolean inUse;
        synchronized (sessionLock) {
            lost = source != null ? source : session;
            inUse = lost != null && session == lost;
            if (inUse) {
                session = null;
            }
        }
        if (lost != null) {
            lost.removeSessionStateListener(this.internalSessionStateListener);
            lost.unbindAndClose();
        }
        return inUse;
    }

    /**
     * Sets the session that a start or a lazy session creation bound, unless the SMSC dropped it already: its loss
     * asked for no reconnect, as it was not in use yet, so the caller fails as for a failed bind.
     */
    private void setNewSession(SMPPSession newSession) throws IOException {
        synchronized (sessionLock) {
            if (!isSessionLost(newSession)) {
                session = newSession;
                return;
            }
        }
        newSession.removeSessionStateListener(this.internalSessionStateListener);
        newSession.unbindAndClose();
        throw new IOException("Lost the connection to " + getEndpoint().getConnectionString() + " right after the bind");
    }

    private void reconnect() {
        reconnectRequested.set(true);
        // a thread that does not get the lock leaves its request to the thread that holds it, which looks for a request
        // again once it is done, so that no request is lost
        while (reconnectRequested.get() && !isServiceStopping(this) && reconnectLock.tryLock()) {
            try {
                // a request whose session was replaced already (by the reconnect of another request) needs nothing
                if (reconnectRequested.getAndSet(false) && isSessionClosed(session)) {
                    runReconnectTask();
                }
            } finally {
                reconnectLock.unlock();
            }
        }
    }

    private void runReconnectTask() {
        int startGeneration = generation.get();
        BackgroundTask task = newReconnectTask(reconnectService, RECONNECT_TASK_NAME,
                configuration.getInitialReconnectDelay(),
                configuration.getReconnectDelay(), configuration.getMaxReconnect());

        reconnectTask = task;
        try {
            // a stop that began before the task was published does not cancel it, and the task would never run
            // on the shut down reconnect service
            if (!isServiceStopping(this)) {
                task.run(getEndpoint().getCamelContext(), () -> doReconnect(startGeneration));
            }
        } finally {
            reconnectTask = null;
        }
    }

    private boolean doReconnect(int startGeneration) {
        try {
            LOG.info("Trying to reconnect to {}", getEndpoint().getConnectionString());
            if (isServiceStopping(this) || startGeneration != generation.get()) {
                // the start this reconnect belongs to was stopped
                return true;
            }

            if (isSessionClosed(session)) {
                return tryCreateSession(startGeneration);
            }

            LOG.info("Nothing to do: the session is not closed");
        } catch (Exception e) {
            LOG.error("Unable to reconnect to {}: {}", getEndpoint().getConnectionString(), e.getMessage(), e);
            return false;
        }

        return true;
    }

    private boolean tryCreateSession(int startGeneration) {
        SMPPSession newSession;
        try {
            newSession = createSession();
        } catch (Exception e) {
            // the failed bind closed the new session; the session in the field is not this attempt's to close, as it
            // may belong to a later start
            LOG.warn("Failed to reconnect to {}", getEndpoint().getConnectionString());
            return false;
        }

        // the start or a lazy creation that sets a session meanwhile finishes first, and its session is kept
        try {
            connectLock.lockInterruptibly();
        } catch (InterruptedException e) {
            // a stop shut the reconnect service down
            Thread.currentThread().interrupt();
            newSession.removeSessionStateListener(this.internalSessionStateListener);
            newSession.unbindAndClose();
            return true;
        }
        boolean lost;
        try {
            synchronized (sessionLock) {
                boolean needed = startGeneration == generation.get() && isSessionClosed(session);
                if (needed && !isSessionLost(newSession)) {
                    session = newSession;
                    return true;
                }
                lost = needed;
            }
        } finally {
            connectLock.unlock();
        }
        newSession.removeSessionStateListener(this.internalSessionStateListener);
        newSession.unbindAndClose();
        if (lost) {
            // the SMSC dropped the new session before it was set, and its loss asked for no reconnect: try again
            LOG.warn("Failed to reconnect to {}: the new session was lost", getEndpoint().getConnectionString());
            return false;
        }
        // the producer was stopped while this attempt connected, or has a session again: the new one is not needed
        return true;
    }

    @Override
    public SmppEndpoint getEndpoint() {
        return (SmppEndpoint) super.getEndpoint();
    }

    /**
     * Returns the smppConfiguration for this producer
     *
     * @return the configuration
     */
    public SmppConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public String toString() {
        return "SmppProducer[" + getEndpoint().getConnectionString() + "]";
    }
}
