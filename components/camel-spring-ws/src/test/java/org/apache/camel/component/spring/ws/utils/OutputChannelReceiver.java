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
package org.apache.camel.component.spring.ws.utils;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.transport.WebServiceMessageReceiver;

/**
 * Used for test to extract the message that was sent
 */
public class OutputChannelReceiver implements WebServiceMessageReceiver {

    private MessageContext messageContext;

    @Override
    public void receive(MessageContext messageContext) throws Exception {
        this.messageContext = messageContext;
    }

    public MessageContext getMessageContext() {
        return messageContext;
    }

    public void clear() {
        this.messageContext = null;
    }

}
