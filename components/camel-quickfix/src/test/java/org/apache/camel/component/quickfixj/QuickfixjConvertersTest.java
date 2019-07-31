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
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.camel.Exchange;
import org.apache.camel.component.quickfixj.converter.QuickfixjConverters;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import quickfix.Acceptor;
import quickfix.DataDictionary;
import quickfix.Initiator;
import quickfix.Message;
import quickfix.SessionFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.field.HopCompID;
import quickfix.field.MsgType;
import quickfix.fix44.Message.Header.NoHops;
import quickfix.mina.ProtocolFactory;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;

public class QuickfixjConvertersTest extends CamelTestSupport {
    private File settingsFile;
    private ClassLoader contextClassLoader;
    private SessionSettings settings;
    private File tempdir;

    private QuickfixjEngine quickfixjEngine;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        settingsFile = File.createTempFile("quickfixj_test_", ".cfg");
        tempdir = settingsFile.getParentFile();
        URL[] urls = new URL[] {tempdir.toURI().toURL()};
       
        contextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader testClassLoader = new URLClassLoader(urls, contextClassLoader);
        Thread.currentThread().setContextClassLoader(testClassLoader);
        
        settings = new SessionSettings();
        settings.setString(Acceptor.SETTING_SOCKET_ACCEPT_PROTOCOL, ProtocolFactory.getTypeString(ProtocolFactory.VM_PIPE));
        settings.setString(Initiator.SETTING_SOCKET_CONNECT_PROTOCOL, ProtocolFactory.getTypeString(ProtocolFactory.VM_PIPE));
    }

    @Override
    @After
    public void tearDown() throws Exception {
        Thread.currentThread().setContextClassLoader(contextClassLoader);

        super.tearDown();
    }

    @Test
    public void convertSessionID() {
        Object value = context.getTypeConverter().convertTo(SessionID.class, "FIX.4.0:FOO->BAR");
        
        assertThat(value, instanceOf(SessionID.class));
        assertThat((SessionID)value, is(new SessionID("FIX.4.0", "FOO", "BAR")));
    }

    @Test
    public void convertToExchange() {
        SessionID sessionID = new SessionID("FIX.4.0", "FOO", "BAR");
        QuickfixjEndpoint endpoint = new QuickfixjEndpoint(null, "", new QuickfixjComponent(new DefaultCamelContext()));
        
        Message message = new Message();     
        message.getHeader().setString(MsgType.FIELD, MsgType.ORDER_SINGLE);
        
        Exchange exchange = QuickfixjConverters.toExchange(endpoint, sessionID, message, QuickfixjEventCategory.AppMessageSent);
        
        assertThat((SessionID)exchange.getIn().getHeader(QuickfixjEndpoint.SESSION_ID_KEY), is(sessionID));
        
        assertThat((QuickfixjEventCategory)exchange.getIn().getHeader(QuickfixjEndpoint.EVENT_CATEGORY_KEY), 
                is(QuickfixjEventCategory.AppMessageSent));
        
        assertThat((String)exchange.getIn().getHeader(QuickfixjEndpoint.MESSAGE_TYPE_KEY), is(MsgType.ORDER_SINGLE));
    }

    @Test
    public void convertToExchangeWithNullMessage() {
        SessionID sessionID = new SessionID("FIX.4.0", "FOO", "BAR");
        QuickfixjEndpoint endpoint = new QuickfixjEndpoint(null, "", new QuickfixjComponent(new DefaultCamelContext()));
        
        Exchange exchange = QuickfixjConverters.toExchange(endpoint, sessionID, null, QuickfixjEventCategory.AppMessageSent);
        
        assertThat((SessionID)exchange.getIn().getHeader(QuickfixjEndpoint.SESSION_ID_KEY), is(sessionID));
        
        assertThat((QuickfixjEventCategory)exchange.getIn().getHeader(QuickfixjEndpoint.EVENT_CATEGORY_KEY), 
                is(QuickfixjEventCategory.AppMessageSent));
        
        assertThat(exchange.getIn().getHeader(QuickfixjEndpoint.MESSAGE_TYPE_KEY), is(nullValue()));
    }

    @Test
    public void convertMessageWithoutRepeatingGroups() {
        String data = "8=FIX.4.0\0019=100\00135=D\00134=2\00149=TW\00156=ISLD\00111=ID\00121=1\001"
            + "40=1\00154=1\00140=2\00138=200\00155=INTC\00110=160\001";
        
        Exchange exchange = new DefaultExchange(context);
        Object value = context.getTypeConverter().convertTo(Message.class, exchange, data);
        
        assertThat(value, instanceOf(Message.class));
    }

    @Test
    public void convertMessageWithRepeatingGroupsUsingSessionID() throws Exception {
        SessionID sessionID = new SessionID("FIX.4.4", "FOO", "BAR");
        
        createSession(sessionID);
        
        try {
            String data = "8=FIX.4.4\0019=40\00135=A\001"
                    + "627=2\001628=FOO\001628=BAR\001"
                    + "98=0\001384=2\001372=D\001385=R\001372=8\001385=S\00110=230\001";

            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setHeader(QuickfixjEndpoint.SESSION_ID_KEY, sessionID);
            exchange.getIn().setBody(data);
            
            Message message = exchange.getIn().getBody(Message.class);
            
            NoHops hop = new NoHops();
            message.getHeader().getGroup(1, hop);
            assertEquals("FOO", hop.getString(HopCompID.FIELD));
            message.getHeader().getGroup(2, hop);
            assertEquals("BAR", hop.getString(HopCompID.FIELD));

        } finally {
            quickfixjEngine.stop();
        }
    }

    @Test
    public void convertMessageWithRepeatingGroupsUsingExchangeDictionary() throws Exception {
        SessionID sessionID = new SessionID("FIX.4.4", "FOO", "BAR");
        
        createSession(sessionID);
        
        try {
            String data = "8=FIX.4.4\0019=40\00135=A\001"
                    + "627=2\001628=FOO\001628=BAR\001"
                    + "98=0\001384=2\001372=D\001385=R\001372=8\001385=S\00110=230\001";

            Exchange exchange = new DefaultExchange(context);
            exchange.setProperty(QuickfixjEndpoint.DATA_DICTIONARY_KEY, new DataDictionary("FIX44.xml"));
            exchange.getIn().setBody(data);
            
            Message message = exchange.getIn().getBody(Message.class);
            
            NoHops hop = new NoHops();
            message.getHeader().getGroup(1, hop);
            assertEquals("FOO", hop.getString(HopCompID.FIELD));
            message.getHeader().getGroup(2, hop);
            assertEquals("BAR", hop.getString(HopCompID.FIELD));

        } finally {
            quickfixjEngine.stop();
        }
    }

    @Test
    public void convertMessageWithRepeatingGroupsUsingExchangeDictionaryResource() throws Exception {
        SessionID sessionID = new SessionID("FIX.4.4", "FOO", "BAR");
        
        createSession(sessionID);
        
        try {
            String data = "8=FIX.4.4\0019=40\00135=A\001"
                    + "627=2\001628=FOO\001628=BAR\001"
                    + "98=0\001384=2\001372=D\001385=R\001372=8\001385=S\00110=230\001";

            Exchange exchange = new DefaultExchange(context);
            exchange.setProperty(QuickfixjEndpoint.DATA_DICTIONARY_KEY, "FIX44.xml");
            exchange.getIn().setBody(data);
            
            Message message = exchange.getIn().getBody(Message.class);
            
            NoHops hop = new NoHops();
            message.getHeader().getGroup(1, hop);
            assertEquals("FOO", hop.getString(HopCompID.FIELD));
            message.getHeader().getGroup(2, hop);
            assertEquals("BAR", hop.getString(HopCompID.FIELD));

        } finally {
            quickfixjEngine.stop();
        }
    }

    private void createSession(SessionID sessionID) throws Exception {
        SessionSettings settings = new SessionSettings();
        settings.setString(Acceptor.SETTING_SOCKET_ACCEPT_PROTOCOL, ProtocolFactory.getTypeString(ProtocolFactory.VM_PIPE));
        
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.ACCEPTOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Acceptor.SETTING_SOCKET_ACCEPT_PORT, 1234);
        TestSupport.setSessionID(settings, sessionID);

        TestSupport.writeSettings(settings, settingsFile);
        
        quickfixjEngine = new QuickfixjEngine("quickfix:test", settingsFile.getName());
        quickfixjEngine.start(); 
    }
}
