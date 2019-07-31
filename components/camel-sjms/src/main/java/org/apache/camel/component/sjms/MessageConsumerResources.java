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
package org.apache.camel.component.sjms;

import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.Session;

public class MessageConsumerResources {

    private final Session session;
    private final MessageConsumer messageConsumer;
    private final Destination replyToDestination;

    public MessageConsumerResources(MessageConsumer messageConsumer) {
        this(null, messageConsumer, null);
    }

    public MessageConsumerResources(Session session, MessageConsumer messageConsumer) {
        this(session, messageConsumer, null);
    }

    public MessageConsumerResources(Session session, MessageConsumer messageConsumer, Destination replyToDestination) {
        this.session = session;
        this.messageConsumer = messageConsumer;
        this.replyToDestination = replyToDestination;
    }

    public Session getSession() {
        return session;
    }

    public MessageConsumer getMessageConsumer() {
        return messageConsumer;
    }

    public Destination getReplyToDestination() {
        return replyToDestination;
    }
}