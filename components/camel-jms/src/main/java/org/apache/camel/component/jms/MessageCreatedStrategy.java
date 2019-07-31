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
package org.apache.camel.component.jms;

import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.Exchange;

/**
 * A strategy that allows custom components to plugin and perform custom logic when Camel creates {@link javax.jms.Message} instance.
 * <p/>
 * For example to populate the message with custom information that are component specific and not part of the JMS specification.
 */
public interface MessageCreatedStrategy {

    /**
     * Callback when the JMS message has <i>just</i> been created, which allows custom modifications afterwards.
     *
     * @param exchange the current exchange
     * @param session the JMS session used to create the message
     * @param cause optional exception occurred that should be sent as reply instead of a regular body
     */
    void onMessageCreated(Message message, Session session, Exchange exchange, Throwable cause);
}
