/**
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.quickfixj.converter.QuickfixjConverters;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.impl.converter.StaticMethodTypeConverter;
import org.apache.mina.common.TransportType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import quickfix.Acceptor;
import quickfix.FixVersions;
import quickfix.Initiator;
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


public class QuickfixjComponentTest {
    private File settingsFile;
    private File tempdir;
    private ClassLoader contextClassLoader;
    private SessionID sessionID;
    private SessionSettings settings;
    private QuickfixjComponent component;

    private void setSessionID(SessionSettings sessionSettings, SessionID sessionID) {
        sessionSettings.setString(sessionID, SessionSettings.BEGINSTRING, sessionID.getBeginString());
        sessionSettings.setString(sessionID, SessionSettings.SENDERCOMPID, sessionID.getSenderCompID());
        sessionSettings.setString(sessionID, SessionSettings.TARGETCOMPID, sessionID.getTargetCompID());
    }
    
    private String getEndpointUri(final String configFilename, SessionID sid) {
        String uri = "quickfixj:" + configFilename;
        if (sid != null) {
            uri += "?sessionID=" + sid;
        }
        return uri;
    }
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
        settings.setString(Acceptor.SETTING_SOCKET_ACCEPT_PROTOCOL, TransportType.VM_PIPE.toString());
        settings.setString(Initiator.SETTING_SOCKET_CONNECT_PROTOCOL, TransportType.VM_PIPE.toString());
        settings.setBool(Session.SETTING_USE_DATA_DICTIONARY, false);
        setSessionID(settings, sessionID);   

        DefaultCamelContext camelContext = new DefaultCamelContext();
        component = new QuickfixjComponent();
        component.setCamelContext(camelContext);
        assertThat(component.getEngines().size(), is(0));

        Method converterMethod = QuickfixjConverters.class.getMethod("toSessionID", new Class<?>[] {String.class});
        camelContext.getTypeConverterRegistry().addTypeConverter(SessionID.class, String.class,  new StaticMethodTypeConverter(converterMethod));
        
    }

    @After
    public void tearDown() throws Exception {
        Thread.currentThread().setContextClassLoader(contextClassLoader);   
        component.stop();
    }

    @Test
    public void createEndpointBeforeComponentStart() throws Exception {
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Initiator.SETTING_SOCKET_CONNECT_PORT, 1234);

        writeSettings();

        Endpoint e1 = component.createEndpoint(getEndpointUri(settingsFile.getName(), null));
        assertThat(component.getEngines().size(), is(1));
        assertThat(component.getEngines().get(settingsFile.getName()), is(notNullValue()));
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(false));
        assertThat(((QuickfixjEndpoint)e1).getSessionID(), is(nullValue()));
        
        // Should used cached QFJ engine
        Endpoint e2 = component.createEndpoint(getEndpointUri(settingsFile.getName(), sessionID));
        
        assertThat(component.getEngines().size(), is(1));
        assertThat(component.getEngines().get(settingsFile.getName()), is(notNullValue()));
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(false));
        assertThat(((QuickfixjEndpoint)e2).getSessionID(), is(sessionID));
        
        component.start();
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(true));
        
        // Move these too an endpoint testcase if one exists
        assertThat(e2.isSingleton(), is(true));
        assertThat(((MultipleConsumersSupport)e2).isMultipleConsumersSupported(), is(true));
    }
    
    @Test
    public void createEndpointAfterComponentStart() throws Exception {
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Initiator.SETTING_SOCKET_CONNECT_PORT, 1234);

        writeSettings();

        component.start();

        Endpoint e1 = component.createEndpoint(getEndpointUri(settingsFile.getName(), null));
        assertThat(component.getEngines().size(), is(1));
        assertThat(component.getEngines().get(settingsFile.getName()), is(notNullValue()));
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(true));
        assertThat(((QuickfixjEndpoint)e1).getSessionID(), is(nullValue()));
        
        // Should used cached QFJ engine
        Endpoint e2 = component.createEndpoint(getEndpointUri(settingsFile.getName(), sessionID));
        
        assertThat(component.getEngines().size(), is(1));
        assertThat(component.getEngines().get(settingsFile.getName()), is(notNullValue()));
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(true));
        assertThat(((QuickfixjEndpoint)e2).getSessionID(), is(sessionID));
    }

    @Test
    public void componentStop() throws Exception {
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.INITIATOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Initiator.SETTING_SOCKET_CONNECT_PORT, 1234);

        writeSettings();

        Endpoint endpoint = component.createEndpoint(getEndpointUri(settingsFile.getName(), null));
        
        final CountDownLatch latch = new CountDownLatch(1);
        
        Consumer consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                QuickfixjEventCategory eventCategory = 
                    (QuickfixjEventCategory) exchange.getIn().getHeader(QuickfixjEndpoint.EVENT_CATEGORY_KEY);
                if (eventCategory == QuickfixjEventCategory.SessionCreated) {
                    latch.countDown();
                }
            }
        });
        
        // Endpoint automatically starts the consumer
        assertThat(((ServiceSupport)consumer).isStarted(), is(true));
        
        component.start();
        
        assertTrue("Session not created", latch.await(5000, TimeUnit.MILLISECONDS));
        
        component.stop();
        
        assertThat(component.getEngines().get(settingsFile.getName()).isStarted(), is(false));
    }

    @Test
    public void messagePublication() throws Exception {
        // Create settings file with both acceptor and initiator
        
        SessionSettings settings = new SessionSettings();
        settings.setString(Acceptor.SETTING_SOCKET_ACCEPT_PROTOCOL, TransportType.VM_PIPE.toString());
        settings.setString(Initiator.SETTING_SOCKET_CONNECT_PROTOCOL, TransportType.VM_PIPE.toString());
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

        writeSettings(settings);
        
        Endpoint endpoint = component.createEndpoint(getEndpointUri(settingsFile.getName(), null));
        
        // Start the component and wait for the FIX sessions to be logged on

        final CountDownLatch logonLatch = new CountDownLatch(2);
        final CountDownLatch messageLatch = new CountDownLatch(2);
                
        endpoint.createConsumer(new Processor() {
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
        
        component.start();
        
        assertTrue("Session not created", logonLatch.await(5000, TimeUnit.MILLISECONDS));
       
        Endpoint producerEndpoint = component.createEndpoint(getEndpointUri(settingsFile.getName(), acceptorSessionID));
        Producer producer = producerEndpoint.createProducer();
        
        // FIX message to send
        Email email = new Email(new EmailThreadID("ID"), new EmailType(EmailType.NEW), new Subject("Test"));
        Exchange exchange = producer.createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody(email);
        
        producer.process(exchange);            

        // Produce with no session ID specified, session ID must be in message
        Producer producer2 = endpoint.createProducer();
         
        email.getHeader().setString(SenderCompID.FIELD, acceptorSessionID.getSenderCompID());
        email.getHeader().setString(TargetCompID.FIELD, acceptorSessionID.getTargetCompID());

        producer2.process(exchange);
       
        assertTrue("Messages not received", messageLatch.await(5000, TimeUnit.MILLISECONDS));
    }

    private void writeSettings() throws IOException {
        writeSettings(settings);
    }

    private void writeSettings(SessionSettings settings) throws IOException {
        FileOutputStream settingsOut = new FileOutputStream(settingsFile);
        try {
            settings.toStream(settingsOut);
        } finally {
            settingsOut.close();
        }
    }
}
