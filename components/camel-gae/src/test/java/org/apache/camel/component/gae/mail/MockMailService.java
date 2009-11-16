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
package org.apache.camel.component.gae.mail;

import java.io.IOException;
import java.util.LinkedList;

import com.google.appengine.api.mail.MailService;

public class MockMailService implements MailService {

    private LinkedList<Message> messages;
    
    public MockMailService() {
        this.messages = new LinkedList<Message>();
    }
    
    public void send(Message message) throws IOException {
        messages.add(message);
    }

    public void sendToAdmins(Message message) throws IOException {
        throw new UnsupportedOperationException("not implemented");
    }

    public Message getFirstMessage() {
        return getMessage(0);
    }
    
    public Message getMessage(int idx) {
        return messages.get(idx);
    }
    
    public void reset() {
        messages.clear();
    }
}
