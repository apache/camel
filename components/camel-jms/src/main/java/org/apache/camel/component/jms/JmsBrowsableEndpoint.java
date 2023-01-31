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

import jakarta.jms.Message;
import jakarta.jms.Session;

import org.apache.camel.Exchange;

/**
 * JMS endpoint which is browsable.
 */
public interface JmsBrowsableEndpoint {

    /**
     * If a number is set > 0 then this limits the number of messages that are returned when browsing the queue
     */
    int getMaximumBrowseSize();

    /**
     * The JMS selector to use (if any)
     */
    String getSelector();

    /**
     * To create exchange for the messages to be browsed.
     */
    Exchange createExchange(Message message, Session session);

}
