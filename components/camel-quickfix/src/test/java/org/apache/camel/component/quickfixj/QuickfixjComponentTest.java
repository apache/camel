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
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.StatefulService;
import org.apache.camel.component.quickfixj.converter.QuickfixjConverters;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.converter.StaticMethodTypeConverter;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.IOHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import quickfix.Acceptor;
import quickfix.DefaultMessageFactory;
import quickfix.FixVersions;
import quickfix.Initiator;
import quickfix.LogFactory;
import quickfix.MemoryStoreFactory;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.ScreenLogFactory;
import quickfix.Session;
import quickfix.SessionFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.field.EmailThreadID;
import quickfix.field.EmailType;
import quickfix.field.SenderCompID;
import quickfix.field.Subject;
import quickfix.field.TargetCompID;
import quickfix.fix44.Email;
import quickfix.mina.ProtocolFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class QuickfixjComponentTest {
    private File settingsFile;
    private File settingsFile2;
    private File tempdir;
    private File tempdir2;
    private ClassLoader contextClassLoader;
    private SessionID sessionID;
    private SessionSettings settings;
    private QuickfixjComponent component;
    private CamelContext camelContext;
    private MessageFactory engineMessageFactory;
    private MessageStoreFactory engineMessageStoreFactory;
    private LogFactory engineLogFactory;
    
    private void setSessionID(SessionSettings sessionSettings, SessionID sessionID) {
        sessionSettings.setString(sessionID, SessionSettings.BEGINSTRING, sessionID.getBeginString());
        sessionSettings.setString(sessionID, SessionSettings.SENDERCOMPID, sessionID.getSenderCompID());
        sessionSettings.setString(sessionID, SessionSettings.TARGETCOMPID, sessionID.getTargetCompID());
    }
    
    private String getEndpointUri(final String configFilename, SessionID sid) {
        String uri = "quickfix:" + configFilename;
        if (sid != null) {
            uri += "?sessionID=" + sid;
        }
        return uri;
    }

    @Before
    public void setUp() throws Exception {
        settingsFile = File.createTempFile("quickfixj_test_", ".cfg");
        settingsFile2 = File.createTempFile("quickfixj_test2_", ".cfg");
        tempdir = settingsFile.getParentFile();
        tempdir2 = settingsFile.getParentFile();
        URL[] urls = new URL[] {tempdir.toURI().toURL(), tempdir2.toURI().toURL()};
        
        sessionID = new SessionID(FixVersions.BEGINSTRING_FIX44, "FOO", "BAR");

        settings = new SessionSettings();
        settings.setString(Acceptor.SETTING_SOCKET_ACCEPT_PROTOCOL, ProtocolFactory.getTypeString(ProtocolFactory.VM_PIPE));
        settings.setString(Initiator.SETTING_SOCKET_CONNECT_PROTOCOL, ProtocolFactory.getTypeString(ProtocolFactory.VM_PIPE));
        settings.setBool(Session.SETTING_USE_DATA_DICTIONARY, false);
        setSessionID(settings, sessionID);   

        contextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader testClassLoader = new URLClassLoader(urls, contextClassLoader);
        Thread.currentThread().setContextClassLoader(testClassLoader);
    }

    private void setUpComponent() throws IOException, MalformedURLException, NoSuchMethodException {
        setUpComponent(false);
    }
    
    private void setUpComponent(boolean injectQfjPlugins) throws IOException, MalformedURLException, NoSuchMethodException {
        camelContext = new DefaultCamelContext();
        component = new QuickfixjComponent();
        component.setCamelContext(camelContext);
        camelContext.addComponent("quickfix", component);
        
        if (injectQfjPlugins) {
            engineMessageFactory = new DefaultMessageFactory();
            engineMessageStoreFactory = new MemoryStoreFactory();
            engineLogFactory = new ScreenLogFactory();
            
            component.setMessageFactory(engineMessageFactory);
            component.setMessageStoreFactory(engineMessageStoreFactory);
            component.setLogFactory(engineLogFactory);
        }
        
        assertThat(component.getEngines().size(), is(0));

        Method converterMethod = QuickfixjConverters.class.getMethod("toSessionID", new Class<?>[] {String.class});
        camelContext.getTypeConverterRegistry().addTypeConverter(SessionID.class, String.class,  new StaticMethodTypeConverter(converterMethod, false));
    }

    @After
    public void tearDown() throws Exception {
        Thread.currentThread().setContextClassLoader(contextClassLoader);   
        if (component != null) {
            component.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    public void createEndpointBeforeComponentStart() throws Exception {
        setUpComponent();

        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Initiator.SETTING_SOCKET_CONNECT_PORT, 1234);

        writeSettings(settings, true);

        // Should use cached QFJ engine
        Endpoint e1 = component.createEndpoint(getEndpointUri(settingsFile.getName(), null));
        assertThat(component.getProvisionalEngines().size(), is(1));
        assertThat(component.getProvisionalEngines().get(settingsFile.getName()), is(notNullValue()));
        assertThat(component.getProvisionalEngines().get(settingsFile.getName()).isInitialized(), is(true));
        assertThat(component.getProvisionalEngines().get(settingsFile.getName()).isStarted(), is(false));
        assertThat(component.getEngines().size(), is(0));
        assertThat(((QuickfixjEndpoint)e1).getSessionID(), is(nullValue()));

        writeSettings(settings, false);

        // Should use cached QFJ engine
        Endpoint e2 = component.createEndpoint(getEndpointUri(settingsFile2.getName(), null));
        assertThat(component.getProvisionalEngines().size(), is(2));
        assertThat(component.getProvisionalEngines().get(settingsFile.getName()), is(notNullValue()));
        assertThat(component.getProvisionalEngines().get(settingsFile.getName()).isInitialized(), is(true));
        assertThat(component.getProvisionalEngines().get(settingsFile.getName()).isStarted(), is(false));
        assertThat(component.getEngines().size(), is(0));
        assertThat(((QuickfixjEndpoint)e2).getSessionID(), is(nullValue()));

        // will start the component
        camelContext.start();

        assertThat(component.getProvisionalEngines().size(), is(0));
        assertThat(component.getEngines().size(), is(2));
        assertThat(component.getEngines().get(settingsFile.getName()).isInitialized(), is(true));
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(true));

        // Move these too an endpoint testcase if one exists
        assertThat(e1.isSingleton(), is(true));
        assertThat(((MultipleConsumersSupport)e1).isMultipleConsumersSupported(), is(true));
        assertThat(e2.isSingleton(), is(true));
        assertThat(((MultipleConsumersSupport)e2).isMultipleConsumersSupported(), is(true));
    }

    @Test
    public void createEndpointAfterComponentStart() throws Exception {
        setUpComponent();

        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Initiator.SETTING_SOCKET_CONNECT_PORT, 1234);

        writeSettings();

        // will start the component
        camelContext.start();

        Endpoint e1 = component.createEndpoint(getEndpointUri(settingsFile.getName(), null));
        assertThat(component.getEngines().size(), is(1));
        assertThat(component.getEngines().get(settingsFile.getName()), is(notNullValue()));
        assertThat(component.getEngines().get(settingsFile.getName()).isInitialized(), is(true));
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(true));
        assertThat(component.getProvisionalEngines().size(), is(0));
        assertThat(((QuickfixjEndpoint)e1).getSessionID(), is(nullValue()));
        
        Endpoint e2 = component.createEndpoint(getEndpointUri(settingsFile.getName(), sessionID));
        assertThat(component.getEngines().size(), is(1));
        assertThat(component.getEngines().get(settingsFile.getName()), is(notNullValue()));
        assertThat(component.getEngines().get(settingsFile.getName()).isInitialized(), is(true));
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(true));
        assertThat(component.getProvisionalEngines().size(), is(0));
        assertThat(((QuickfixjEndpoint)e2).getSessionID(), is(sessionID));
    }

    @Test
    public void createEnginesLazily() throws Exception {
        setUpComponent();
        component.setLazyCreateEngines(true);

        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Initiator.SETTING_SOCKET_CONNECT_PORT, 1234);

        writeSettings();

        // start the component
        camelContext.start();

        QuickfixjEndpoint e1 = (QuickfixjEndpoint) component.createEndpoint(getEndpointUri(settingsFile.getName(), null));
        assertThat(component.getEngines().size(), is(1));
        assertThat(component.getProvisionalEngines().size(), is(0));
        assertThat(component.getEngines().get(settingsFile.getName()), is(notNullValue()));
        assertThat(component.getEngines().get(settingsFile.getName()).isInitialized(), is(false));
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(false));

        e1.ensureInitialized();
        assertThat(component.getEngines().get(settingsFile.getName()).isInitialized(), is(true));
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(true));
    }

    @Test
    public void createEndpointsInNonLazyComponent() throws Exception {
        setUpComponent();
        // configuration will be done per endpoint
        component.setLazyCreateEngines(false);

        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Initiator.SETTING_SOCKET_CONNECT_PORT, 1234);

        writeSettings();

        // will start the component
        camelContext.start();

        QuickfixjEndpoint e1 = (QuickfixjEndpoint) component.createEndpoint(getEndpointUri(settingsFile.getName(), null) + "?lazyCreateEngine=true");
        assertThat(component.getEngines().get(settingsFile.getName()).isInitialized(), is(false));
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(false));
        assertThat(component.getEngines().get(settingsFile.getName()).isLazy(), is(true));

        e1.ensureInitialized();
        assertThat(component.getEngines().get(settingsFile.getName()).isInitialized(), is(true));
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(true));

        writeSettings(settings, false);

        // will use connector's lazyCreateEngines setting 
        component.createEndpoint(getEndpointUri(settingsFile2.getName(), sessionID));
        assertThat(component.getEngines().get(settingsFile2.getName()).isInitialized(), is(true));
        assertThat(component.getEngines().get(settingsFile2.getName()).isStarted(), is(true));
        assertThat(component.getEngines().get(settingsFile2.getName()).isLazy(), is(false));
    }

    @Test
    public void createEndpointsInLazyComponent() throws Exception {
        setUpComponent();
        component.setLazyCreateEngines(true);

        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Initiator.SETTING_SOCKET_CONNECT_PORT, 1234);

        writeSettings();

        // will start the component
        camelContext.start();

        // will use connector's lazyCreateEngines setting
        QuickfixjEndpoint e1 = (QuickfixjEndpoint) component.createEndpoint(getEndpointUri(settingsFile.getName(), null));
        assertThat(component.getEngines().get(settingsFile.getName()).isInitialized(), is(false));
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(false));
        assertThat(component.getEngines().get(settingsFile.getName()).isLazy(), is(true));

        e1.ensureInitialized();
        assertThat(component.getEngines().get(settingsFile.getName()).isInitialized(), is(true));
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(true));

        writeSettings(settings, false);

        // will override connector's lazyCreateEngines setting
        component.createEndpoint(getEndpointUri(settingsFile2.getName(), sessionID) + "&lazyCreateEngine=false");
        assertThat(component.getEngines().get(settingsFile2.getName()).isInitialized(), is(true));
        assertThat(component.getEngines().get(settingsFile2.getName()).isStarted(), is(true));
        assertThat(component.getEngines().get(settingsFile2.getName()).isLazy(), is(false));
    }

    @Test
    public void componentStop() throws Exception {
        setUpComponent();

        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Initiator.SETTING_SOCKET_CONNECT_PORT, 1234);

        writeSettings();

        Endpoint endpoint = component.createEndpoint(getEndpointUri(settingsFile.getName(), null));
        
        final CountDownLatch latch = new CountDownLatch(1);
        
        Consumer consumer = endpoint.createConsumer(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                QuickfixjEventCategory eventCategory = 
                    (QuickfixjEventCategory) exchange.getIn().getHeader(QuickfixjEndpoint.EVENT_CATEGORY_KEY);
                if (eventCategory == QuickfixjEventCategory.SessionCreated) {
                    latch.countDown();
                }
            }
        });
        ServiceHelper.startService(consumer);

        // Endpoint automatically starts the consumer
        assertThat(((StatefulService)consumer).isStarted(), is(true));

        // will start the component
        camelContext.start();

        assertTrue("Session not created", latch.await(5000, TimeUnit.MILLISECONDS));
        
        component.stop();
        
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(false));
        // it should still be initialized (ready to start again)
        assertThat(component.getEngines().get(settingsFile.getName()).isInitialized(), is(true));
    }

    @Test
    public void messagePublication() throws Exception {
        setUpComponent();

        // Create settings file with both acceptor and initiator
        
        SessionSettings settings = new SessionSettings();        
        settings.setString(Acceptor.SETTING_SOCKET_ACCEPT_PROTOCOL, ProtocolFactory.getTypeString(ProtocolFactory.VM_PIPE));
        settings.setString(Initiator.SETTING_SOCKET_CONNECT_PROTOCOL, ProtocolFactory.getTypeString(ProtocolFactory.VM_PIPE));
        settings.setBool(Session.SETTING_USE_DATA_DICTIONARY, false);
        
        SessionID acceptorSessionID =  new SessionID(FixVersions.BEGINSTRING_FIX44, "ACCEPTOR", "INITIATOR");
        settings.setString(acceptorSessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.ACCEPTOR_CONNECTION_TYPE);
        settings.setLong(acceptorSessionID, Acceptor.SETTING_SOCKET_ACCEPT_PORT, 1234);
        setSessionID(settings, acceptorSessionID);
        
        SessionID initiatorSessionID = new SessionID(FixVersions.BEGINSTRING_FIX44, "INITIATOR", "ACCEPTOR");
        settings.setString(initiatorSessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);
        settings.setLong(initiatorSessionID, Initiator.SETTING_SOCKET_CONNECT_PORT, 1234);
        settings.setLong(initiatorSessionID, Initiator.SETTING_RECONNECT_INTERVAL, 1);
        setSessionID(settings, initiatorSessionID);

        writeSettings(settings, true);
        
        Endpoint endpoint = component.createEndpoint(getEndpointUri(settingsFile.getName(), null));
        
        // Start the component and wait for the FIX sessions to be logged on

        final CountDownLatch logonLatch = new CountDownLatch(2);
        final CountDownLatch messageLatch = new CountDownLatch(2);
                
        Consumer consumer = endpoint.createConsumer(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                QuickfixjEventCategory eventCategory = 
                    (QuickfixjEventCategory) exchange.getIn().getHeader(QuickfixjEndpoint.EVENT_CATEGORY_KEY);
                if (eventCategory == QuickfixjEventCategory.SessionLogon) {
                    logonLatch.countDown();
                } else if (eventCategory == QuickfixjEventCategory.AppMessageReceived) {
                    messageLatch.countDown();
                }
            }
        });
        ServiceHelper.startService(consumer);

        // will start the component
        camelContext.start();

        assertTrue("Session not created", logonLatch.await(5000, TimeUnit.MILLISECONDS));
       
        Endpoint producerEndpoint = component.createEndpoint(getEndpointUri(settingsFile.getName(), acceptorSessionID));
        Producer producer = producerEndpoint.createProducer();
        
        // FIX message to send
        Email email = new Email(new EmailThreadID("ID"), new EmailType(EmailType.NEW), new Subject("Test"));
        Exchange exchange = producer.getEndpoint().createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody(email);
        
        producer.process(exchange);            

        // Produce with no session ID specified, session ID must be in message
        Producer producer2 = endpoint.createProducer();
         
        email.getHeader().setString(SenderCompID.FIELD, acceptorSessionID.getSenderCompID());
        email.getHeader().setString(TargetCompID.FIELD, acceptorSessionID.getTargetCompID());

        producer2.process(exchange);
       
        assertTrue("Messages not received", messageLatch.await(5000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void userSpecifiedQuickfixjPlugins() throws Exception {
        setUpComponent(true);

        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Initiator.SETTING_SOCKET_CONNECT_PORT, 1234);

        writeSettings();

        component.createEndpoint(getEndpointUri(settingsFile.getName(), null));

        // will start the component
        camelContext.start();

        assertThat(component.getEngines().size(), is(1));
        QuickfixjEngine engine = component.getEngines().values().iterator().next();
        
        assertThat(engine.getMessageFactory(), is(engineMessageFactory));
        assertThat(engine.getMessageStoreFactory(), is(engineMessageStoreFactory));
        assertThat(engine.getLogFactory(), is(engineLogFactory));
    }

    private void writeSettings() throws IOException {
        writeSettings(settings, true);
    }

    private void writeSettings(SessionSettings settings, boolean firstSettingsFile) throws IOException {
        FileOutputStream settingsOut = new FileOutputStream(firstSettingsFile ? settingsFile : settingsFile2);
        try {
            settings.toStream(settingsOut);
        } finally {
            IOHelper.close(settingsOut);
        }
    }
}
