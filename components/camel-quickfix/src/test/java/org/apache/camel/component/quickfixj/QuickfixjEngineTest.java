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

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import quickfix.Acceptor;
import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.FieldConvertError;
import quickfix.FieldNotFound;
import quickfix.FileLogFactory;
import quickfix.FileStoreFactory;
import quickfix.FixVersions;
import quickfix.Initiator;
import quickfix.JdbcLogFactory;
import quickfix.JdbcSetting;
import quickfix.JdbcStoreFactory;
import quickfix.LogFactory;
import quickfix.MemoryStoreFactory;
import quickfix.Message;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.SLF4JLogFactory;
import quickfix.ScreenLogFactory;
import quickfix.Session;
import quickfix.SessionFactory;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.SessionSettings;
import quickfix.SleepycatStoreFactory;
import quickfix.SocketAcceptor;
import quickfix.SocketInitiator;
import quickfix.ThreadedSocketAcceptor;
import quickfix.ThreadedSocketInitiator;
import quickfix.field.MsgType;
import quickfix.fix42.Email;
import quickfix.mina.ProtocolFactory;

import static org.apache.camel.util.ObjectHelper.equal;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;

public class QuickfixjEngineTest extends org.apache.camel.test.junit4.TestSupport {
    private File settingsFile;
    private ClassLoader contextClassLoader;
    private SessionSettings settings;
    private SessionID sessionID;
    private File tempdir;
    private QuickfixjEngine quickfixjEngine;

    @Before
    public void setUp() throws Exception {
        settingsFile = File.createTempFile("quickfixj_test_", ".cfg");
        tempdir = settingsFile.getParentFile();
        URL[] urls = new URL[] {tempdir.toURI().toURL()};

        contextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader testClassLoader = new URLClassLoader(urls, contextClassLoader);
        Thread.currentThread().setContextClassLoader(testClassLoader);

        sessionID = new SessionID(FixVersions.BEGINSTRING_FIX44, "FOO", "BAR");

        settings = new SessionSettings();
        settings.setString(Acceptor.SETTING_SOCKET_ACCEPT_PROTOCOL, ProtocolFactory.getTypeString(ProtocolFactory.VM_PIPE));
        settings.setString(Initiator.SETTING_SOCKET_CONNECT_PROTOCOL, ProtocolFactory.getTypeString(ProtocolFactory.VM_PIPE));
        settings.setBool(Session.SETTING_USE_DATA_DICTIONARY, false);
        settings.setBool(QuickfixjEngine.SETTING_USE_JMX, false);
        TestSupport.setSessionID(settings, sessionID);
    }

    @After
    public void tearDown() throws Exception {
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        if (quickfixjEngine != null) {
            quickfixjEngine.stop();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingSettingsResource() throws Exception {
        new QuickfixjEngine("quickfix:test", "bogus.cfg");
    }

    @Test
    public void defaultInitiator() throws Exception {
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());

        assertThat(quickfixjEngine.getInitiator(), instanceOf(SocketInitiator.class));
        assertThat(quickfixjEngine.getAcceptor(), nullValue());
        assertDefaultConfiguration(quickfixjEngine);
    }

    @Test
    public void threadPerSessionInitiator() throws Exception {
        settings.setString(QuickfixjEngine.SETTING_THREAD_MODEL, QuickfixjEngine.ThreadModel.ThreadPerSession.toString());
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());

        assertThat(quickfixjEngine.getInitiator(), instanceOf(ThreadedSocketInitiator.class));
        assertThat(quickfixjEngine.getAcceptor(), nullValue());
        assertDefaultConfiguration(quickfixjEngine);
    }

    @Test
    public void defaultAcceptor() throws Exception {
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.ACCEPTOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Acceptor.SETTING_SOCKET_ACCEPT_PORT, 1234);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());

        assertThat(quickfixjEngine.getInitiator(), nullValue());
        assertThat(quickfixjEngine.getAcceptor(), instanceOf(SocketAcceptor.class));
        assertDefaultConfiguration(quickfixjEngine);
    }

    @Test
    public void threadPerSessionAcceptor() throws Exception {
        settings.setString(QuickfixjEngine.SETTING_THREAD_MODEL, QuickfixjEngine.ThreadModel.ThreadPerSession.toString());
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.ACCEPTOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Acceptor.SETTING_SOCKET_ACCEPT_PORT, 1234);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());

        assertThat(quickfixjEngine.getInitiator(), nullValue());
        assertThat(quickfixjEngine.getAcceptor(), instanceOf(ThreadedSocketAcceptor.class));
        assertDefaultConfiguration(quickfixjEngine);
    }

    @Test
    public void minimalInitiatorAndAcceptor() throws Exception {
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.ACCEPTOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Acceptor.SETTING_SOCKET_ACCEPT_PORT, 1234);

        SessionID initiatorSessionID = new SessionID(FixVersions.BEGINSTRING_FIX44, "FARGLE", "BARGLE");
        settings.setString(initiatorSessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);
        TestSupport.setSessionID(settings, initiatorSessionID);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());

        assertThat(quickfixjEngine.getInitiator(), notNullValue());
        assertThat(quickfixjEngine.getAcceptor(), notNullValue());
        assertDefaultConfiguration(quickfixjEngine);
    }

    @Test
    public void inferFileStore() throws Exception {
        settings.setString(FileStoreFactory.SETTING_FILE_STORE_PATH, tempdir.toString());
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());

        assertThat(quickfixjEngine.getInitiator(), notNullValue());
        assertThat(quickfixjEngine.getAcceptor(), nullValue());
        assertThat(quickfixjEngine.getUri(), is("quickfix:test"));
        assertThat(quickfixjEngine.getMessageStoreFactory(), instanceOf(FileStoreFactory.class));
        assertThat(quickfixjEngine.getLogFactory(), instanceOf(ScreenLogFactory.class));
        assertThat(quickfixjEngine.getMessageFactory(), instanceOf(DefaultMessageFactory.class));
    }

    // NOTE This is a little strange. If the JDBC driver is set and no log settings are found,
    // then we use JDBC for both the message store and the log.

    @Test
    public void inferJdbcStoreAndLog() throws Exception {
        // If there is a setting of the LOG_EVENT_TABLE, we should create a jdbcLogFactory for it
        settings.setString(JdbcSetting.SETTING_JDBC_DRIVER, "driver");
        settings.setString(JdbcSetting.SETTING_LOG_EVENT_TABLE, "table");

        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());

        assertThat(quickfixjEngine.getInitiator(), notNullValue());
        assertThat(quickfixjEngine.getAcceptor(), nullValue());
        assertThat(quickfixjEngine.getMessageStoreFactory(), instanceOf(JdbcStoreFactory.class));
        assertThat(quickfixjEngine.getLogFactory(), instanceOf(JdbcLogFactory.class));
        assertThat(quickfixjEngine.getMessageFactory(), instanceOf(DefaultMessageFactory.class));
    }

    @Test
    public void inferJdbcStoreViaJNDI() throws Exception {
        // If there is a setting of the LOG_EVENT_TABLE, we should create a jdbcLogFactory for it
        settings.setString(JdbcSetting.SETTING_JDBC_DS_NAME, "ds_name");
        settings.setString(JdbcSetting.SETTING_LOG_EVENT_TABLE, "table");

        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());

        assertThat(quickfixjEngine.getInitiator(), notNullValue());
        assertThat(quickfixjEngine.getAcceptor(), nullValue());
        assertThat(quickfixjEngine.getMessageStoreFactory(), instanceOf(JdbcStoreFactory.class));
        assertThat(quickfixjEngine.getLogFactory(), instanceOf(JdbcLogFactory.class));
        assertThat(quickfixjEngine.getMessageFactory(), instanceOf(DefaultMessageFactory.class));
    }

    @Test
    public void ambiguousMessageStore() throws Exception {
        settings.setString(FileStoreFactory.SETTING_FILE_STORE_PATH, tempdir.toString());
        settings.setString(JdbcSetting.SETTING_JDBC_DRIVER, "driver");
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);

        writeSettings();

        doAmbiguityTest("Ambiguous message store");
    }

    @Test
    public void inferJdbcStoreWithInferredLog() throws Exception {
        settings.setString(JdbcSetting.SETTING_JDBC_DRIVER, "driver");
        settings.setBool(ScreenLogFactory.SETTING_LOG_EVENTS, true);
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());

        assertThat(quickfixjEngine.getInitiator(), notNullValue());
        assertThat(quickfixjEngine.getAcceptor(), nullValue());
        assertThat(quickfixjEngine.getMessageStoreFactory(), instanceOf(JdbcStoreFactory.class));
        assertThat(quickfixjEngine.getLogFactory(), instanceOf(ScreenLogFactory.class));
        assertThat(quickfixjEngine.getMessageFactory(), instanceOf(DefaultMessageFactory.class));
    }

    @Test
    public void inferSleepycatStore() throws Exception {
        settings.setString(SleepycatStoreFactory.SETTING_SLEEPYCAT_DATABASE_DIR, tempdir.toString());
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());

        assertThat(quickfixjEngine.getInitiator(), notNullValue());
        assertThat(quickfixjEngine.getAcceptor(), nullValue());
        assertThat(quickfixjEngine.getMessageStoreFactory(), instanceOf(SleepycatStoreFactory.class));
        assertThat(quickfixjEngine.getLogFactory(), instanceOf(ScreenLogFactory.class));
        assertThat(quickfixjEngine.getMessageFactory(), instanceOf(DefaultMessageFactory.class));
    }

    @Test
    public void inferFileLog() throws Exception {
        settings.setString(FileLogFactory.SETTING_FILE_LOG_PATH, tempdir.toString());
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());

        assertThat(quickfixjEngine.getInitiator(), notNullValue());
        assertThat(quickfixjEngine.getAcceptor(), nullValue());
        assertThat(quickfixjEngine.getMessageStoreFactory(), instanceOf(MemoryStoreFactory.class));
        assertThat(quickfixjEngine.getLogFactory(), instanceOf(FileLogFactory.class));
        assertThat(quickfixjEngine.getMessageFactory(), instanceOf(DefaultMessageFactory.class));
    }

    @Test
    public void inferSlf4jLog() throws Exception {
        settings.setString(SLF4JLogFactory.SETTING_EVENT_CATEGORY, "Events");
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());

        assertThat(quickfixjEngine.getInitiator(), notNullValue());
        assertThat(quickfixjEngine.getAcceptor(), nullValue());
        assertThat(quickfixjEngine.getMessageStoreFactory(), instanceOf(MemoryStoreFactory.class));
        assertThat(quickfixjEngine.getLogFactory(), instanceOf(SLF4JLogFactory.class));
        assertThat(quickfixjEngine.getMessageFactory(), instanceOf(DefaultMessageFactory.class));
    }

    @Test
    public void ambiguousLog() throws Exception {
        settings.setString(FileLogFactory.SETTING_FILE_LOG_PATH, tempdir.toString());
        settings.setBool(ScreenLogFactory.SETTING_LOG_EVENTS, true);
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);

        writeSettings();

        doAmbiguityTest("Ambiguous log");
    }

    private void doAmbiguityTest(String exceptionText) throws FieldConvertError, IOException, JMException {
        try {
            quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());
            fail("Expected exception, but none raised");
        } catch (ConfigError e) {
            assertTrue(e.getMessage().contains(exceptionText));
        }
    }

    @Test
    public void useExplicitComponentImplementations() throws Exception {
        settings.setString(SLF4JLogFactory.SETTING_EVENT_CATEGORY, "Events");
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);

        writeSettings();

        MessageStoreFactory messageStoreFactory = Mockito.mock(MessageStoreFactory.class);
        LogFactory logFactory = Mockito.mock(LogFactory.class);
        MessageFactory messageFactory = Mockito.mock(MessageFactory.class);

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName(), messageStoreFactory, logFactory, messageFactory);

        assertThat(quickfixjEngine.getMessageStoreFactory(), is(messageStoreFactory));
        assertThat(quickfixjEngine.getLogFactory(), is(logFactory));
        assertThat(quickfixjEngine.getMessageFactory(), is(messageFactory));
    }

    @Test
    public void enableJmxForInitiator() throws Exception {
        settings.setBool(QuickfixjEngine.SETTING_USE_JMX, true);
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Initiator.SETTING_SOCKET_CONNECT_PORT, 1234);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());
        quickfixjEngine.start();

        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> n = mbeanServer.queryNames(new ObjectName("org.quickfixj:type=Connector,role=Initiator,*"), null);
        assertFalse("QFJ mbean not registered", n.isEmpty());
    }

    @Test
    public void enableJmxForAcceptor() throws Exception {
        settings.setBool(QuickfixjEngine.SETTING_USE_JMX, true);
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.ACCEPTOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Acceptor.SETTING_SOCKET_ACCEPT_PORT, 1234);

        writeSettings();

        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());
        quickfixjEngine.start();

        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> n = mbeanServer.queryNames(new ObjectName("org.quickfixj:type=Connector,role=Acceptor,*"), null);
        assertFalse("QFJ mbean not registered", n.isEmpty());
    }

    @Test
    public void sessionEvents() throws Exception {
        SessionID acceptorSessionID = new SessionID(FixVersions.BEGINSTRING_FIX42, "MARKET", "TRADER");
        SessionID initiatorSessionID = new SessionID(FixVersions.BEGINSTRING_FIX42, "TRADER", "MARKET");

        quickfixjEngine = new QuickfixjEngine("quickfix:test", "examples/inprocess.cfg");

        doLogonEventsTest(acceptorSessionID, initiatorSessionID, quickfixjEngine);

        doApplicationMessageEventsTest(acceptorSessionID, initiatorSessionID, quickfixjEngine);

        doLogoffEventsTest(acceptorSessionID, initiatorSessionID, quickfixjEngine);
    }

    private void doLogonEventsTest(SessionID acceptorSessionID, SessionID initiatorSessionID, QuickfixjEngine quickfixjEngine) throws Exception {

        final List<EventRecord> events = new ArrayList<>();
        final CountDownLatch logonLatch = new CountDownLatch(2);

        QuickfixjEventListener logonListener = new QuickfixjEventListener() {
            @Override
            public synchronized void onEvent(QuickfixjEventCategory eventCategory, SessionID sessionID, Message message) {
                events.add(new EventRecord(eventCategory, sessionID, message));
                if (eventCategory == QuickfixjEventCategory.SessionLogon) {
                    logonLatch.countDown();
                }
            }
        };

        quickfixjEngine.addEventListener(logonListener);

        quickfixjEngine.start();

        assertTrue("Logons not completed", logonLatch.await(5000, TimeUnit.MILLISECONDS));
        quickfixjEngine.removeEventListener(logonListener);

        assertThat(events.size(), is(8));

        // The session events will arrive out of order as the event callbacks happen in the context of different threads so that the asserts
        // below must cater for that, that's do not assert on the order of the arrived events but just do assert on their existence. for this
        // to work we have've defined a relaxed comparison about the messages being sent, see the EventRecord.equals() method
        assertTrue(events.contains(new EventRecord(QuickfixjEventCategory.SessionCreated, acceptorSessionID, null)));
        assertTrue(events.contains(new EventRecord(QuickfixjEventCategory.SessionCreated, initiatorSessionID, null)));
        assertTrue(events.contains(new EventRecord(QuickfixjEventCategory.AdminMessageSent, initiatorSessionID, new Message())));
        assertTrue(events.contains(new EventRecord(QuickfixjEventCategory.AdminMessageReceived, acceptorSessionID, new Message())));
        assertTrue(events.contains(new EventRecord(QuickfixjEventCategory.AdminMessageSent, acceptorSessionID, new Message())));
        assertTrue(events.contains(new EventRecord(QuickfixjEventCategory.AdminMessageReceived, initiatorSessionID, new Message())));
        assertTrue(events.contains(new EventRecord(QuickfixjEventCategory.SessionLogon, initiatorSessionID, null)));
        assertTrue(events.contains(new EventRecord(QuickfixjEventCategory.SessionLogon, acceptorSessionID, null)));
    }

    private void doApplicationMessageEventsTest(SessionID acceptorSessionID, SessionID initiatorSessionID, QuickfixjEngine quickfixjEngine) throws SessionNotFound,
        InterruptedException, FieldNotFound {

        final List<EventRecord> events = new ArrayList<>();
        final CountDownLatch messageLatch = new CountDownLatch(1);

        QuickfixjEventListener messageListener = new QuickfixjEventListener() {
            @Override
            public synchronized void onEvent(QuickfixjEventCategory eventCategory, SessionID sessionID, Message message) {
                EventRecord event = new EventRecord(eventCategory, sessionID, message);
                events.add(event);
                if (eventCategory == QuickfixjEventCategory.AppMessageReceived) {
                    messageLatch.countDown();
                }
            }
        };

        quickfixjEngine.addEventListener(messageListener);
        Email email = TestSupport.createEmailMessage("Test");
        Session.sendToTarget(email, initiatorSessionID);

        assertTrue("Application message not received", messageLatch.await(5000, TimeUnit.MILLISECONDS));
        quickfixjEngine.removeEventListener(messageListener);

        assertThat(events.size(), is(2));

        EventRecord sendEvent = new EventRecord(QuickfixjEventCategory.AppMessageSent, initiatorSessionID, new Message());
        assertTrue(events.contains(sendEvent));
        int sendEventIndex = events.indexOf(sendEvent);
        assertThat(events.get(sendEventIndex).message.getHeader().getString(MsgType.FIELD), is(MsgType.EMAIL));

        EventRecord receiveEvent = new EventRecord(QuickfixjEventCategory.AppMessageReceived, acceptorSessionID, new Message());
        assertTrue(events.contains(receiveEvent));
        int receiveEventIndex = events.indexOf(receiveEvent);
        assertThat(events.get(receiveEventIndex).message.getHeader().getString(MsgType.FIELD), is(MsgType.EMAIL));
    }

    private void doLogoffEventsTest(SessionID acceptorSessionID, SessionID initiatorSessionID, QuickfixjEngine quickfixjEngine) throws Exception {

        final List<EventRecord> events = new ArrayList<>();
        final CountDownLatch logoffLatch = new CountDownLatch(2);

        QuickfixjEventListener logoffListener = new QuickfixjEventListener() {
            @Override
            public synchronized void onEvent(QuickfixjEventCategory eventCategory, SessionID sessionID, Message message) {
                EventRecord event = new EventRecord(eventCategory, sessionID, message);
                events.add(event);
                if (eventCategory == QuickfixjEventCategory.SessionLogoff) {
                    logoffLatch.countDown();
                }
            }
        };

        quickfixjEngine.addEventListener(logoffListener);

        quickfixjEngine.stop();

        assertTrue("Logoffs not received", logoffLatch.await(5000, TimeUnit.MILLISECONDS));
        quickfixjEngine.removeEventListener(logoffListener);

        assertThat(events.size(), is(6));

        assertTrue(events.contains(new EventRecord(QuickfixjEventCategory.SessionLogoff, acceptorSessionID, null)));
        assertTrue(events.contains(new EventRecord(QuickfixjEventCategory.SessionLogoff, initiatorSessionID, null)));
    }

    private static class EventRecord {
        final QuickfixjEventCategory eventCategory;
        final SessionID sessionID;
        final Message message;

        EventRecord(QuickfixjEventCategory eventCategory, SessionID sessionID, Message message) {
            this.eventCategory = eventCategory;
            this.sessionID = sessionID;
            this.message = message;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (eventCategory == null ? 0 : eventCategory.hashCode());
            result = prime * result + (sessionID == null ? 0 : sessionID.hashCode());
            result = prime * result + (message == null ? 1231 : 1237);

            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof EventRecord)) {
                return false;
            }

            EventRecord other = (EventRecord) obj;
            boolean answer = equal(eventCategory, other.eventCategory) && equal(sessionID, other.sessionID);

            // we do just require a "relaxed" comparison of the messages, that's they should both be either null or both not null.
            // this is required so that we can properly assert on the events being fired
            if (message == null) {
                answer &= other.message == null;
            } else {
                answer &= other.message != null;
            }

            return answer;
        }

        @Override
        public String toString() {
            return "EventRecord [eventCategory=" + eventCategory + ", sessionID=" + sessionID + ", message=" + message + "]";
        }
    }

    private void assertDefaultConfiguration(QuickfixjEngine quickfixjEngine) throws Exception {
        assertThat(quickfixjEngine.getMessageStoreFactory(), instanceOf(MemoryStoreFactory.class));
        assertThat(quickfixjEngine.getLogFactory(), instanceOf(ScreenLogFactory.class));
        assertThat(quickfixjEngine.getMessageFactory(), instanceOf(DefaultMessageFactory.class));

        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> names = mbeanServer.queryNames(new ObjectName("org.quickfixj:*"), null);
        assertTrue("QFJ Connector/Initiator mbeans should not have been registered", names.isEmpty());
    }

    private void writeSettings() throws IOException {
        TestSupport.writeSettings(settings, settingsFile);
    }

}
