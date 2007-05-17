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
package org.apache.camel.component.cxf;

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.local.LocalTransportFactory;

/**
 * A consumer of exchanges for a service in CXF
 *
 * @version $Revision$
 */
public class CxfConsumer extends DefaultConsumer<CxfExchange> {
    private CxfEndpoint endpoint;
    private final LocalTransportFactory transportFactory;
    private Destination destination;

    public CxfConsumer(CxfEndpoint endpoint, Processor processor, LocalTransportFactory transportFactory) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.transportFactory = transportFactory;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        destination = transportFactory.getDestination(endpoint.getEndpointInfo());
        destination.setMessageObserver(new MessageObserver() {
            public void onMessage(Message message) {
                incomingCxfMessage(message);
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        if (destination != null) {
            destination.shutdown();
        }
        super.doStop();
    }

    protected void incomingCxfMessage(Message message) {
        try {
            CxfExchange exchange = endpoint.createExchange(message);
			getProcessor().process(exchange);
		} catch (Exception e) {			
			// TODO: what do do if we are getting processing errors from camel?  Shutdown?
			e.printStackTrace();
		}
    }
}
