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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.local.LocalConduit;
import org.apache.cxf.transport.local.LocalTransportFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Sends messages from Camel into the CXF endpoint
 *
 * @version $Revision$
 */
public class CxfProducer extends DefaultProducer {
    private CxfEndpoint endpoint;
    private final LocalTransportFactory transportFactory;
    private Destination destination;
    private Conduit conduit;
    private ResultFuture future = new ResultFuture();

    public CxfProducer(CxfEndpoint endpoint, LocalTransportFactory transportFactory) {
        super(endpoint);
        this.endpoint = endpoint;
        this.transportFactory = transportFactory;
    }

    public void process(Exchange exchange) {
        CxfExchange cxfExchange = endpoint.toExchangeType(exchange);
        process(cxfExchange);
    }

    public void process(CxfExchange exchange) {
        try {
            CxfBinding binding = endpoint.getBinding();
            MessageImpl m = binding.createCxfMessage(exchange);
            ExchangeImpl e = new ExchangeImpl();
            e.setInMessage(m);
            m.put(LocalConduit.DIRECT_DISPATCH, Boolean.TRUE);
            m.setDestination(destination);
            synchronized (conduit) {
                conduit.prepare(m);

                // now lets wait for the response
                if (endpoint.isInOut()) {
                    Message response = future.getResponse();

                    // TODO - why do we need to ignore the returned message and get the out message from the exchange!
                    response = e.getOutMessage();
                    binding.storeCxfResponse(exchange, response);
                }
            }
        }
        catch (IOException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        EndpointInfo endpointInfo = endpoint.getEndpointInfo();
        destination = transportFactory.getDestination(endpointInfo);

        // Set up a listener for the response
        conduit = transportFactory.getConduit(endpointInfo);
        conduit.setMessageObserver(future);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (conduit != null) {
            conduit.close();
        }
    }

    protected class ResultFuture implements MessageObserver {
        Message response;
        CountDownLatch latch = new CountDownLatch(1);

        public Message getResponse() {
            while (response == null) {
                try {
                    latch.await();
                }
                catch (InterruptedException e) {
                    // ignore
                }
            }
            return response;
        }

        public synchronized void onMessage(Message message) {
            try {
                message.remove(LocalConduit.DIRECT_DISPATCH);
                this.response = message;
            }
            finally {
                latch.countDown();
            }
        }
    }
}
