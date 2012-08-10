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
package org.apache.camel.component.sjms;

import java.util.concurrent.Exchanger;

import javax.jms.Message;
import javax.jms.MessageListener;

import org.apache.camel.component.sjms.jms.ConnectionResource;
import org.apache.camel.component.sjms.jms.SessionPool;

/**
 * TODO Add Class documentation for SjmsMessageConsumer
 *
 */
public interface SjmsMessageConsumer extends MessageListener {
    void handleMessage(Message message);
    SjmsMessageConsumer createMessageConsumer(ConnectionResource connectionResource, String destinationName) throws Exception;
    SjmsMessageConsumer createMessageConsumerListener(SessionPool sessionPool, String destinationName, Exchanger<Object> exchanger) throws Exception;
    void destroyMessageConsumer() throws Exception;
}
