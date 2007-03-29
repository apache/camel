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
import org.apache.camel.impl.DefaultProducer;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.local.LocalConduit;
import org.apache.cxf.transport.local.LocalDestination;
import org.apache.cxf.transport.local.LocalTransportFactory;
import org.xmlsoap.schemas.wsdl.http.AddressType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

/**
 * Sends messages from Camel into the CXF endpoint
 *
 * @version $Revision$
 */
public class CxfProducer extends DefaultProducer<CxfExchange> {
    private CxfEndpoint endpoint;

    public CxfProducer(CxfEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void onExchange(CxfExchange exchange) {
        try {
            LocalTransportFactory factory = endpoint.getLocalTransportFactory();
            EndpointInfo endpointInfo = endpoint.getEndpointInfo();
            LocalDestination d = (LocalDestination) factory.getDestination(endpointInfo);

            // Set up a listener for the response
            Conduit conduit = factory.getConduit(endpointInfo);
            ResultFuture future = new ResultFuture();
            conduit.setMessageObserver(future);

            CxfBinding binding = endpoint.getBinding();
            MessageImpl m = binding.createCxfMessage(exchange);
            ExchangeImpl e = new ExchangeImpl();
            e.setInMessage(m);
            m.put(LocalConduit.DIRECT_DISPATCH, Boolean.TRUE);
            m.setDestination(d);
            conduit.send(m);

            // now lets wait for the response
            if (endpoint.isInOut()) {
                Message response = future.getResponse();
                binding.storeCxfResponse(exchange, response);
            }
        }
        catch (IOException e) {
            throw new RuntimeCamelException(e);
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
