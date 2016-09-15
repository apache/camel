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
package org.apache.camel.component.asterisk;

import org.apache.camel.Exchange;
import org.asteriskjava.manager.ManagerEventListener;
import org.asteriskjava.manager.event.ManagerEvent;

public class AsteriskListenerTask extends Thread implements ManagerEventListener {
    private final AsteriskEndpoint endpoint;
    private final AsteriskConsumer consumer;

    public AsteriskListenerTask(AsteriskEndpoint endpoint, AsteriskConsumer consumer) {
        super();
        this.endpoint = endpoint;
        this.consumer = consumer;
    }

    @Override
    public void onManagerEvent(ManagerEvent event) {
        Exchange exchange = endpoint.createExchange(event);
        try {
            consumer.getProcessor().process(exchange);
        } catch (Exception e) {
            consumer.getExceptionHandler().handleException("Error processing exchange.", exchange, e);
        }
    }

}
