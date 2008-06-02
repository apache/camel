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
import java.io.OutputStream;

import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

public class NullConduit implements Conduit {

    public void close() {
    }

    public void close(Message message) throws IOException {
        OutputStream outputStream = message.getContent(OutputStream.class);
        if (outputStream != null) {
            outputStream.close();
        }
    }

    public Destination getBackChannel() {
        return null;
    }

    public EndpointReferenceType getTarget() {
        return null;
    }

    public void prepare(Message message) throws IOException {
        CachedOutputStream outputStream = new CachedOutputStream();
        message.setContent(OutputStream.class, outputStream);
    }

    public void setMessageObserver(MessageObserver observer) {
    }

}
