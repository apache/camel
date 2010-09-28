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
}
