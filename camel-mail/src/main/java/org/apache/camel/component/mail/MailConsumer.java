/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mail;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.event.TransportEvent;
import javax.mail.event.TransportListener;

/**
 * A {@link Consumer} which consumes messages from JavaMail using a {@link Transport} and dispatches them
 * to the {@link Processor}
 *
 * @version $Revision: 523430 $
 */
public class MailConsumer extends DefaultConsumer<MailExchange> implements TransportListener {
    private final MailEndpoint endpoint;
    private final Transport transport;

    public MailConsumer(MailEndpoint endpoint, Processor<MailExchange> processor, Transport transport) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.transport = transport;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        transport.addTransportListener(this);
    }

    @Override
    protected void doStop() throws Exception {
        transport.close();
        super.doStop();
    }

    public void messageDelivered(TransportEvent transportEvent) {
        Message message = transportEvent.getMessage();
        MailExchange exchange = endpoint.createExchange(message);
        getProcessor().process(exchange);
    }

    public void messageNotDelivered(TransportEvent transportEvent) {
    }

    public void messagePartiallyDelivered(TransportEvent transportEvent) {
    }
}
