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
package org.apache.camel.component.sjms.jms;

import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.Endpoint;

public interface JmsObjectFactory {
    MessageConsumer createMessageConsumer(Session session, Endpoint endpoint) throws Exception;

    MessageConsumer createMessageConsumer(Session session, Destination destination,
            String messageSelector, boolean topic, String subscriptionId, boolean durable,
            boolean shared) throws Exception;

    MessageConsumer createMessageConsumer(Session session, Destination destination,
            String messageSelector, boolean topic, String subscriptionId, boolean durable,
            boolean shared, boolean noLocal) throws Exception;

    MessageProducer createMessageProducer(Session session, Endpoint endpoint) throws Exception;

    MessageProducer createMessageProducer(Session session, Destination destination,
            boolean persistent, long ttl) throws Exception;
}
