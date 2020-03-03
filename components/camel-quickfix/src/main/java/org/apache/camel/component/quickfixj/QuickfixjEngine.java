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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.JMException;
import javax.management.ObjectName;

import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.quickfixj.jmx.JmxExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Acceptor;
import quickfix.Application;
import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.DoNotSend;
import quickfix.FieldConvertError;
import quickfix.FieldNotFound;
import quickfix.FileLogFactory;
import quickfix.FileStoreFactory;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Initiator;
import quickfix.JdbcLogFactory;
import quickfix.JdbcSetting;
import quickfix.JdbcStoreFactory;
import quickfix.LogFactory;
import quickfix.MemoryStoreFactory;
import quickfix.Message;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.RejectLogon;
import quickfix.SLF4JLogFactory;
import quickfix.ScreenLogFactory;
import quickfix.Session;
import quickfix.SessionFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.SleepycatStoreFactory;
import quickfix.SocketAcceptor;
import quickfix.SocketInitiator;
import quickfix.ThreadedSocketAcceptor;
import quickfix.ThreadedSocketInitiator;
import quickfix.UnsupportedMessageType;

/**
 * This is a wrapper class that provided QuickFIX/J initialization capabilities
 * beyond those supported in the core QuickFIX/J distribution.
 * 
 * Specifically, it infers dependencies on specific implementations of message
 * stores and logs. It also supports extended QuickFIX/J settings properties to
 * specify threading models, custom store and log implementations, etc.
 * 
 * The wrapper will create an initiator or acceptor or both depending on the
 * roles of sessions described in the settings file.
 */
public class QuickfixjEngine extends ServiceSupport {
    public static final String DEFAULT_START_TIME = "00:00:00";
    public static final String DEFAULT_END_TIME = "00:00:00";
    public static final long DEFAULT_HEARTBTINT = 30;
    public static final String SETTING_THREAD_MODEL = "ThreadModel";
    public static final String SETTING_USE_JMX = "UseJmx";

    private static final Logger LOG = LoggerFactory.getLogger(QuickfixjEngine.class);

    private Acceptor acceptor;
    private Initiator initiator;
    private JmxExporter jmxExporter;
    private MessageStoreFactory messageStoreFactory;
    private LogFactory sessionLogFactory;
    private MessageFactory messageFactory;
    private final MessageCorrelator messageCorrelator = new MessageCorrelator();
    private List<QuickfixjEventListener> eventListeners = new CopyOnWriteArrayList<>();
    private final String uri;
    private ObjectName acceptorObjectName;
    private ObjectName initiatorObjectName;
    private final SessionSettings settings;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private boolean lazy;

    public enum ThreadModel {
        ThreadPerConnector, ThreadPerSession;
    }

    /**
     * @deprecated Better make use of the {@link #QuickfixjEngine(String, String)} constructor
     *             as the {@code forcedShutdown} paramater had/has no effect.
     */
    @Deprecated
    public QuickfixjEngine(String uri, String settingsResourceName, boolean forcedShutdown)
        throws ConfigError, FieldConvertError, IOException, JMException {

        this(uri, settingsResourceName, forcedShutdown, null, null, null);
    }

    public QuickfixjEngine(String uri, String settingsResourceName) throws ConfigError, FieldConvertError, IOException, JMException {
        this(uri, settingsResourceName, null, null, null);
    }

    /**
     * @deprecated Better make use of the {@link #QuickfixjEngine(String, String, MessageStoreFactory, LogFactory, MessageFactory)} constructor
     *             as the {@code forcedShutdown} paramater had/has no effect.
     */
    @Deprecated
    public QuickfixjEngine(String uri, String settingsResourceName, boolean forcedShutdown,
            MessageStoreFactory messageStoreFactoryOverride, LogFactory sessionLogFactoryOverride,
            MessageFactory messageFactoryOverride) throws ConfigError, FieldConvertError, IOException, JMException {
        this(uri, loadSettings(settingsResourceName), forcedShutdown, messageStoreFactoryOverride,
                sessionLogFactoryOverride, messageFactoryOverride);
    }

    public QuickfixjEngine(String uri, String settingsResourceName, MessageStoreFactory messageStoreFactoryOverride, LogFactory sessionLogFactoryOverride,
                           MessageFactory messageFactoryOverride) throws ConfigError, FieldConvertError, IOException, JMException {
        this(uri, loadSettings(settingsResourceName), messageStoreFactoryOverride, sessionLogFactoryOverride, messageFactoryOverride);
    }

    /**
     * @deprecated Better make use of the {@link #QuickfixjEngine(String, SessionSettings, MessageStoreFactory, LogFactory, MessageFactory)} constructor
     *             as the {@code forcedShutdown} paramater had/has no effect.
     */
    @Deprecated
    public QuickfixjEngine(String uri, SessionSettings settings, boolean forcedShutdown,
            MessageStoreFactory messageStoreFactoryOverride, LogFactory sessionLogFactoryOverride,
            MessageFactory messageFactoryOverride) throws ConfigError, FieldConvertError, IOException, JMException {
        this(uri, settings, messageStoreFactoryOverride, sessionLogFactoryOverride, messageFactoryOverride);
    }

    public QuickfixjEngine(String uri, SessionSettings settings, MessageStoreFactory messageStoreFactoryOverride, LogFactory sessionLogFactoryOverride,
                           MessageFactory messageFactoryOverride) throws ConfigError, FieldConvertError, IOException, JMException {
        this(uri, settings, messageStoreFactoryOverride, sessionLogFactoryOverride, messageFactoryOverride, false);
    }

    public QuickfixjEngine(String uri, SessionSettings settings, MessageStoreFactory messageStoreFactoryOverride, LogFactory sessionLogFactoryOverride,
            MessageFactory messageFactoryOverride, boolean lazy) throws ConfigError, FieldConvertError, IOException, JMException {
        addEventListener(messageCorrelator);

        this.uri = uri;
        this.lazy = lazy;
        this.settings = settings;

        // overrides
        if (messageFactoryOverride != null) {
            messageFactory = messageFactoryOverride;
        }
        if (sessionLogFactoryOverride != null) {
            sessionLogFactory = sessionLogFactoryOverride;
        }
        if (messageStoreFactoryOverride != null) {
            messageStoreFactory = messageStoreFactoryOverride;
        }

        if (!lazy) {
            initializeEngine();
        }
    }

    /**
     * Initializes the engine on demand. May be called immediately in constructor or when needed.
     * If initializing later, it should be started afterwards.
     */
    void initializeEngine() throws ConfigError,
            FieldConvertError, JMException {
        if (messageFactory == null) {
            messageFactory = new DefaultMessageFactory();
        }
        if (sessionLogFactory == null) {
            sessionLogFactory = inferLogFactory(settings);
        }
        if (messageStoreFactory == null) {
            messageStoreFactory = inferMessageStoreFactory(settings);
        }

        // Set default session schedule if not specified in configuration
        if (!settings.isSetting(Session.SETTING_START_TIME)) {
            settings.setString(Session.SETTING_START_TIME, DEFAULT_START_TIME);
        }
        if (!settings.isSetting(Session.SETTING_END_TIME)) {
            settings.setString(Session.SETTING_END_TIME, DEFAULT_END_TIME);
        }
        // Default heartbeat interval
        if (!settings.isSetting(Session.SETTING_HEARTBTINT)) {
            settings.setLong(Session.SETTING_HEARTBTINT, DEFAULT_HEARTBTINT);
        }

        // Allow specification of the QFJ threading model
        ThreadModel threadModel = ThreadModel.ThreadPerConnector;
        if (settings.isSetting(SETTING_THREAD_MODEL)) {
            threadModel = ThreadModel.valueOf(settings.getString(SETTING_THREAD_MODEL));
        }

        if (settings.isSetting(SETTING_USE_JMX) && settings.getBool(SETTING_USE_JMX)) {
            LOG.info("Enabling JMX for QuickFIX/J");
            jmxExporter = new JmxExporter();
        } else {
            jmxExporter = null;
        }

        // From original component implementation...
        // To avoid this exception in OSGi platform
        // java.lang.NoClassDefFoundError: quickfix/fix41/MessageFactory
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            if (isConnectorRole(settings, SessionFactory.ACCEPTOR_CONNECTION_TYPE)) {
                acceptor = createAcceptor(new Dispatcher(), settings, messageStoreFactory, 
                    sessionLogFactory, messageFactory, threadModel);
            } else {
                acceptor = null;
            }

            if (isConnectorRole(settings, SessionFactory.INITIATOR_CONNECTION_TYPE)) {
                initiator = createInitiator(new Dispatcher(), settings, messageStoreFactory, 
                    sessionLogFactory, messageFactory, threadModel);
            } else {
                initiator = null;
            }

            if (acceptor == null && initiator == null) {
                throw new ConfigError("No connector role");
            }
        } finally {
            Thread.currentThread().setContextClassLoader(ccl);
        }
        initialized.set(true);
    }

    static SessionSettings loadSettings(String settingsResourceName) throws ConfigError {
        InputStream inputStream = ObjectHelper.loadResourceAsStream(settingsResourceName);
        if (inputStream == null) {
            throw new IllegalArgumentException("Could not load " + settingsResourceName);
        }
        return new SessionSettings(inputStream);
    }

    @Override
    protected void doStart() throws Exception {
        if (acceptor != null) {
            acceptor.start();
            if (jmxExporter != null) {
                acceptorObjectName = jmxExporter.register(acceptor);
            }
        }
        if (initiator != null) {
            initiator.start();
            if (jmxExporter != null) {
                initiatorObjectName = jmxExporter.register(initiator);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (acceptor != null) {
            acceptor.stop();

            if (jmxExporter != null && acceptorObjectName != null) {
                jmxExporter.getMBeanServer().unregisterMBean(acceptorObjectName);
            }
        }
        if (initiator != null) {
            initiator.stop();

            if (jmxExporter != null && initiatorObjectName != null) {
                jmxExporter.getMBeanServer().unregisterMBean(initiatorObjectName);
            }
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        // also clear event listeners
        eventListeners.clear();
    }

    private Initiator createInitiator(Application application, SessionSettings settings,
            MessageStoreFactory messageStoreFactory, LogFactory sessionLogFactory, 
            MessageFactory messageFactory, ThreadModel threadModel) throws ConfigError {
        
        Initiator initiator;
        if (threadModel == ThreadModel.ThreadPerSession) {
            initiator = new ThreadedSocketInitiator(application, messageStoreFactory, settings, sessionLogFactory, messageFactory);
        } else if (threadModel == ThreadModel.ThreadPerConnector) {
            initiator = new SocketInitiator(application, messageStoreFactory, settings, sessionLogFactory, messageFactory);
        } else {
            throw new ConfigError("Unknown thread mode: " + threadModel);
        }
        return initiator;
    }

    private Acceptor createAcceptor(Application application, SessionSettings settings,
            MessageStoreFactory messageStoreFactory, LogFactory sessionLogFactory, 
            MessageFactory messageFactory, ThreadModel threadModel) throws ConfigError {

        Acceptor acceptor;
        if (threadModel == ThreadModel.ThreadPerSession) {
            acceptor = new ThreadedSocketAcceptor(application, messageStoreFactory, settings, sessionLogFactory, messageFactory);
        } else if (threadModel == ThreadModel.ThreadPerConnector) {
            acceptor = new SocketAcceptor(application, messageStoreFactory, settings, sessionLogFactory, messageFactory);
        } else {
            throw new ConfigError("Unknown thread mode: " + threadModel);
        }
        return acceptor;
    }

    private MessageStoreFactory inferMessageStoreFactory(SessionSettings settings) throws ConfigError {
        Set<MessageStoreFactory> impliedMessageStoreFactories = new HashSet<>();
        isJdbcStore(settings, impliedMessageStoreFactories);
        isFileStore(settings, impliedMessageStoreFactories);
        isSleepycatStore(settings, impliedMessageStoreFactories);
        if (impliedMessageStoreFactories.size() > 1) {
            throw new ConfigError("Ambiguous message store implied in configuration.");
        }
        MessageStoreFactory messageStoreFactory;
        if (impliedMessageStoreFactories.size() == 1) {
            messageStoreFactory = impliedMessageStoreFactories.iterator().next();
        } else {
            messageStoreFactory = new MemoryStoreFactory();
        }
        LOG.info("Inferring message store factory: {}", messageStoreFactory.getClass().getName());
        return messageStoreFactory;
    }

    private void isSleepycatStore(SessionSettings settings, Set<MessageStoreFactory> impliedMessageStoreFactories) {
        if (settings.isSetting(SleepycatStoreFactory.SETTING_SLEEPYCAT_DATABASE_DIR)) {
            impliedMessageStoreFactories.add(new SleepycatStoreFactory(settings));
        }
    }

    private void isFileStore(SessionSettings settings, Set<MessageStoreFactory> impliedMessageStoreFactories) {
        if (settings.isSetting(FileStoreFactory.SETTING_FILE_STORE_PATH)) {
            impliedMessageStoreFactories.add(new FileStoreFactory(settings));
        }
    }

    private void isJdbcStore(SessionSettings settings, Set<MessageStoreFactory> impliedMessageStoreFactories) {
        if (settings.isSetting(JdbcSetting.SETTING_JDBC_DRIVER) || settings.isSetting(JdbcSetting.SETTING_JDBC_DS_NAME)) {
            impliedMessageStoreFactories.add(new JdbcStoreFactory(settings));
        }
    }

    private LogFactory inferLogFactory(SessionSettings settings) throws ConfigError {
        Set<LogFactory> impliedLogFactories = new HashSet<>();
        isFileLog(settings, impliedLogFactories);
        isScreenLog(settings, impliedLogFactories);
        isSL4JLog(settings, impliedLogFactories);
        isJdbcLog(settings, impliedLogFactories);
        if (impliedLogFactories.size() > 1) {
            throw new ConfigError("Ambiguous log factory implied in configuration");
        }
        LogFactory sessionLogFactory;
        if (impliedLogFactories.size() == 1) {
            sessionLogFactory = impliedLogFactories.iterator().next();
        } else {
            // Default
            sessionLogFactory = new ScreenLogFactory(settings);
        }
        LOG.info("Inferring log factory: {}", sessionLogFactory.getClass().getName());
        return sessionLogFactory;
    }

    private void isScreenLog(SessionSettings settings, Set<LogFactory> impliedLogFactories) {
        if (settings.isSetting(ScreenLogFactory.SETTING_LOG_EVENTS)
                || settings.isSetting(ScreenLogFactory.SETTING_LOG_INCOMING)
                || settings.isSetting(ScreenLogFactory.SETTING_LOG_OUTGOING)) {
            impliedLogFactories.add(new ScreenLogFactory(settings));
        }
    }

    private void isFileLog(SessionSettings settings, Set<LogFactory> impliedLogFactories) {
        if (settings.isSetting(FileLogFactory.SETTING_FILE_LOG_PATH)) {
            impliedLogFactories.add(new FileLogFactory(settings));
        }
    }

    private void isJdbcLog(SessionSettings settings, Set<LogFactory> impliedLogFactories) {
        if ((settings.isSetting(JdbcSetting.SETTING_JDBC_DRIVER) || settings.isSetting(JdbcSetting.SETTING_JDBC_DS_NAME))
                && settings.isSetting(JdbcSetting.SETTING_LOG_EVENT_TABLE)) {
            impliedLogFactories.add(new JdbcLogFactory(settings));
        }
    }

    private void isSL4JLog(SessionSettings settings, Set<LogFactory> impliedLogFactories) {
        for (Object key : settings.getDefaultProperties().keySet()) {
            if (key.toString().startsWith("SLF4J")) {
                impliedLogFactories.add(new SLF4JLogFactory(settings));
                return;
            }
        }
    }

    private boolean isConnectorRole(SessionSettings settings, String connectorRole) throws ConfigError {
        boolean hasRole = false;
        Iterator<SessionID> sessionIdItr = settings.sectionIterator();
        while (sessionIdItr.hasNext()) {
            if (connectorRole.equals(settings.getString(sessionIdItr.next(), SessionFactory.SETTING_CONNECTION_TYPE))) {
                hasRole = true;
                break;
            }
        }
        return hasRole;
    }
    
    public void addEventListener(QuickfixjEventListener listener) {
        eventListeners.add(listener);
    }
    
    public void removeEventListener(QuickfixjEventListener listener) {
        eventListeners.remove(listener);
    }

    private class Dispatcher implements Application {
        @Override
        public void fromAdmin(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
            try {
                dispatch(QuickfixjEventCategory.AdminMessageReceived, sessionID, message);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                rethrowIfType(e, FieldNotFound.class);
                rethrowIfType(e, IncorrectDataFormat.class);
                rethrowIfType(e, IncorrectTagValue.class);
                rethrowIfType(e, RejectLogon.class);               
                throw new DispatcherException(e);
            }
        }
        
        @Override
        public void fromApp(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
            try {
                dispatch(QuickfixjEventCategory.AppMessageReceived, sessionID, message);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                rethrowIfType(e, FieldNotFound.class);
                rethrowIfType(e, IncorrectDataFormat.class);
                rethrowIfType(e, IncorrectTagValue.class);
                rethrowIfType(e, UnsupportedMessageType.class);
                throw new DispatcherException(e);
            }
        }

        @Override
        public void onCreate(SessionID sessionID) {
            try {
                dispatch(QuickfixjEventCategory.SessionCreated, sessionID, null);
            } catch (Exception e) {
                throw new DispatcherException(e);
            }
        }

        @Override
        public void onLogon(SessionID sessionID) {
            try {
                dispatch(QuickfixjEventCategory.SessionLogon, sessionID, null);
            } catch (Exception e) {
                throw new DispatcherException(e);
            }
        }

        @Override
        public void onLogout(SessionID sessionID) {
            try {
                dispatch(QuickfixjEventCategory.SessionLogoff, sessionID, null);
            } catch (Exception e) {
                throw new DispatcherException(e);
            }
        }

        @Override
        public void toAdmin(Message message, SessionID sessionID) {
            try {
                dispatch(QuickfixjEventCategory.AdminMessageSent, sessionID, message);
            } catch (Exception e) {
                throw new DispatcherException(e);
            }
        }

        @Override
        public void toApp(Message message, SessionID sessionID) throws DoNotSend {
            try {
                dispatch(QuickfixjEventCategory.AppMessageSent, sessionID, message);
            } catch (Exception e) {
                throw new DispatcherException(e);
            }
        }

        private <T extends Exception> void rethrowIfType(Exception e, Class<T> exceptionClass) throws T {
            if (e.getClass() == exceptionClass) {
                throw exceptionClass.cast(e);
            }
        }

        private void dispatch(QuickfixjEventCategory quickfixjEventCategory, SessionID sessionID, Message message) throws Exception {
            LOG.debug("FIX event dispatched: {} {}", quickfixjEventCategory, message != null ? message : "");
            for (QuickfixjEventListener listener : eventListeners) {
                // Exceptions propagate back to the FIX engine so sequence numbers can be adjusted
                listener.onEvent(quickfixjEventCategory, sessionID, message);
            }
        }

        private class DispatcherException extends RuntimeException {

            private static final long serialVersionUID = 1L;

            DispatcherException(Throwable cause) {
                super(cause);
            }
        }
    }

    public String getUri() {
        return uri;
    }

    public MessageCorrelator getMessageCorrelator() {
        return messageCorrelator;
    }

    public boolean isInitialized() {
        return this.initialized.get();
    }

    public boolean isLazy() {
        return this.lazy;
    }

    // For Testing
    Initiator getInitiator() {
        return initiator;
    }

    // For Testing
    Acceptor getAcceptor() {
        return acceptor;
    }

    // For Testing
    MessageStoreFactory getMessageStoreFactory() {
        return messageStoreFactory;
    }

    // For Testing
    LogFactory getLogFactory() {
        return sessionLogFactory;
    }

    // For Testing
    MessageFactory getMessageFactory() {
        return messageFactory;
    }
}
