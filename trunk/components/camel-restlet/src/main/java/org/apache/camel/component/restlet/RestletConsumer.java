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
package org.apache.camel.component.restlet;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.Restlet;
import org.restlet.data.Request;
import org.restlet.data.Response;

/**
 * A Restlet consumer acts as a server to listen client requests.
 *
 * @version $Revision$
 */
public class RestletConsumer extends DefaultConsumer {
    private static final Log LOG = LogFactory.getLog(RestletConsumer.class);
    private Restlet restlet;

    public RestletConsumer(Endpoint endpoint, Processor processor) 
        throws Exception {
        super(endpoint, processor);
        
        restlet = new Restlet() {
            @Override
            public void handle(Request request, Response response) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Consumer restlet handle request method: " + request.getMethod());
                }
                
                try {
                    Exchange exchange = getEndpoint().createExchange();
                    RestletBinding binding = ((RestletEndpoint)getEndpoint()).getRestletBinding();
                    binding.populateExchangeFromRestletRequest(request, exchange);
                    getProcessor().process(exchange);
                    binding.populateRestletResponseFromExchange(exchange, response);
                } catch (Exception e) {
                    throw new RuntimeCamelException(e);
                }
            }
        };
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ((RestletEndpoint)getEndpoint()).connect(this);
    }

    @Override
    public void doStop() throws Exception {
        ((RestletEndpoint)getEndpoint()).disconnect(this);
        super.doStop();
    }

    public Restlet getRestlet() {
        return restlet;
    }

}
