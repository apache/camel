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
package org.apache.camel.component.cometd;

import java.util.Collection;
import java.util.Iterator;

import dojox.cometd.Client;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.cometd.AbstractBayeux;

/**
 * A Producer to send messages using Cometd and Bayeux protocol.
 * 
 * @version $Revision: 712626 $
 */
public class CometdProducer extends DefaultProducer implements CometdProducerConsumer {

    @SuppressWarnings("unused")
    private static final transient Log LOG = LogFactory.getLog(CometdProducer.class);
    
    private AbstractBayeux       bayeux;  
    private final CometdEndpoint endpoint;

    public CometdProducer(CometdEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void start() throws Exception {
        super.start();
        endpoint.connect(this);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        endpoint.disconnect(this);
    }

    public void process(final Exchange exchange) {
        Collection<Client> clients = bayeux.getClients();
        for (Iterator<Client> iterator = clients.iterator(); iterator.hasNext();) {
            Client client = (Client) iterator.next();
            client.deliver(client, endpoint.getPath(), exchange.getIn().getBody(), null);
        }
    }

    public CometdEndpoint getEndpoint() {
        return endpoint;
    }
    
    public AbstractBayeux getBayeux() {
        return bayeux;
    }

    public void setBayeux(AbstractBayeux bayeux) {
        this.bayeux = bayeux;
    }
}
