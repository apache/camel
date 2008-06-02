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
package org.apache.camel.component.cxf.util;

import java.io.IOException;

import org.apache.cxf.message.Message;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

public class NullDestination implements Destination {
    MessageObserver messageObserver;

    public NullDestination() {
        // do nothing here
    }

    public EndpointReferenceType getAddress() {
        return null;
    }

    public Conduit getBackChannel(Message inMessage, Message partialResponse, EndpointReferenceType address) throws IOException {
        return null;
    }

    public MessageObserver getMessageObserver() {
        return messageObserver;
    }

    public void shutdown() {
        messageObserver = null;
    }

    public void setMessageObserver(MessageObserver observer) {
        messageObserver = observer;
    }

}
