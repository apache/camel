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
package org.apache.camel.coap;


import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.CoapExchange;

/**
 * The CoAP consumer.
 */
public class CoAPConsumer extends DefaultConsumer {
    private final CoAPEndpoint endpoint;
    private CoapResource resource;

    public CoAPConsumer(final CoAPEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        
        String path = endpoint.getUri().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        this.resource = new CoapResource(path) {

            @Override
            public void handleRequest(Exchange exchange) {
                CoapExchange cexchange = new CoapExchange(exchange, this);
                org.apache.camel.Exchange camelExchange = endpoint.createExchange();
                byte bytes[] = exchange.getCurrentRequest().getPayload();
                camelExchange.getIn().setBody(bytes);
                try {
                    processor.process(camelExchange);
                    
                    
                    Message target = camelExchange.hasOut() ? camelExchange.getOut() : camelExchange.getIn();
                    
                    cexchange.respond(ResponseCode.CONTENT, target.getBody(byte[].class));

                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            
        };
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpoint.getCoapServer().add(resource);
    }

    @Override
    protected void doStop() throws Exception {
        endpoint.getCoapServer().remove(resource);
        super.doStop();
    }
}
