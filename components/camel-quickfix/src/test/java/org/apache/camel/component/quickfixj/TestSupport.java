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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import javax.management.JMException;

import org.mockito.Mockito;

import quickfix.Acceptor;
import quickfix.Application;
import quickfix.ConfigError;
import quickfix.DefaultSessionFactory;
import quickfix.FieldConvertError;
import quickfix.LogFactory;
import quickfix.MessageFactory;
import quickfix.MessageStore;
import quickfix.MessageStoreFactory;
import quickfix.Session;
import quickfix.SessionFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.field.EmailThreadID;
import quickfix.field.EmailType;
import quickfix.field.Subject;
import quickfix.field.Text;
import quickfix.fix42.Email;

public final class TestSupport {
    private TestSupport() {
        // Utility class
    }

    public static void writeSettings(SessionSettings settings, File settingsFile) throws IOException {
        FileOutputStream settingsOut = new FileOutputStream(settingsFile);
        try {
            settings.toStream(settingsOut);
        }  finally {
            settingsOut.close();
        }
    }

    public static void setSessionID(SessionSettings sessionSettings, SessionID sessionID) {
        sessionSettings.setString(sessionID, SessionSettings.BEGINSTRING, sessionID.getBeginString());
        sessionSettings.setString(sessionID, SessionSettings.SENDERCOMPID, sessionID.getSenderCompID());
        sessionSettings.setString(sessionID, SessionSettings.TARGETCOMPID, sessionID.getTargetCompID());
    }

    public static Email createEmailMessage(String subject) {
        Email email = new Email(new EmailThreadID("ID"), new EmailType(EmailType.NEW), new Subject(subject));
        Email.LinesOfText text = new Email.LinesOfText();
        text.set(new Text("Content"));
        email.addGroup(text);
        return email;
    }
    
    public static Session createSession(SessionID sessionID) throws ConfigError, IOException {
        MessageStoreFactory mockMessageStoreFactory = Mockito.mock(MessageStoreFactory.class);
        MessageStore mockMessageStore = Mockito.mock(MessageStore.class);
        Mockito.when(mockMessageStore.getCreationTime()).thenReturn(new Date());
        
        Mockito.when(mockMessageStoreFactory.create(sessionID)).thenReturn(mockMessageStore);

        DefaultSessionFactory factory = new DefaultSessionFactory(
            Mockito.mock(Application.class),
            mockMessageStoreFactory,
            Mockito.mock(LogFactory.class));
        
        SessionSettings settings = new SessionSettings();
        settings.setLong(Session.SETTING_HEARTBTINT, 10);
        settings.setString(Session.SETTING_START_TIME, "00:00:00");
        settings.setString(Session.SETTING_END_TIME, "00:00:00");
        settings.setString(SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.ACCEPTOR_CONNECTION_TYPE);
        settings.setBool(Session.SETTING_USE_DATA_DICTIONARY, false);
        
        return factory.create(sessionID, settings);
    }

    public static QuickfixjEngine createEngine() throws ConfigError, FieldConvertError, IOException, JMException {       
        return createEngine(false);
    }

    public static QuickfixjEngine createEngine(boolean lazy) throws ConfigError, FieldConvertError, IOException, JMException {       
        SessionID sessionID = new SessionID("FIX.4.4:SENDER->TARGET");

        MessageStoreFactory mockMessageStoreFactory = Mockito.mock(MessageStoreFactory.class);
        MessageStore mockMessageStore = Mockito.mock(MessageStore.class);
        Mockito.when(mockMessageStore.getCreationTime()).thenReturn(new Date());
        Mockito.when(mockMessageStoreFactory.create(sessionID)).thenReturn(mockMessageStore);
        
        SessionSettings settings = new SessionSettings();
        
        settings.setLong(sessionID, Session.SETTING_HEARTBTINT, 10);
        settings.setString(sessionID, Session.SETTING_START_TIME, "00:00:00");
        settings.setString(sessionID, Session.SETTING_END_TIME, "00:00:00");
        settings.setString(sessionID, SessionFactory.SETTING_CONNECTION_TYPE, SessionFactory.ACCEPTOR_CONNECTION_TYPE);
        settings.setLong(sessionID, Acceptor.SETTING_SOCKET_ACCEPT_PORT, 8000);
        settings.setBool(sessionID, Session.SETTING_USE_DATA_DICTIONARY, false);
        
        return new QuickfixjEngine("", settings, 
            mockMessageStoreFactory, 
            Mockito.mock(LogFactory.class), 
            Mockito.mock(MessageFactory.class), lazy);
    }
}
