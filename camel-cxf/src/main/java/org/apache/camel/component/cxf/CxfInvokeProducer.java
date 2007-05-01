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
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientFactoryBean;

import java.util.List;

/**
 * Sends messages from Camel into the CXF endpoint
 *
 * @version $Revision$
 */
public class CxfInvokeProducer extends DefaultProducer {
    private CxfInvokeEndpoint endpoint;
    private Client client;

    public CxfInvokeProducer(CxfInvokeEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) {
        CxfExchange cxfExchange = endpoint.toExchangeType(exchange);
        process(cxfExchange);
        exchange.copyFrom(cxfExchange);
    }

    public void process(CxfExchange exchange) {
        List params = exchange.getIn().getBody(List.class);
        Object[] response = null;
        try {
            response = client.invoke(endpoint.getProperty(CxfConstants.METHOD), params.toArray());
        }                                                           
        catch (Exception e) {
            throw new RuntimeCamelException(e);
        }

        CxfBinding binding = endpoint.getBinding();
        binding.storeCxfResponse(exchange, response);
    }

    @Override
    protected void doStart() throws Exception {
        // TODO Add support for sending message inputstream.  Currently, we only handle
        // method invocation with pojo.

        // TODO Add support for endpoints associated with a WSDL
        if (client == null) {
            ClientFactoryBean cfBean = new ClientFactoryBean();
            cfBean.setAddress(getEndpoint().getEndpointUri());
            cfBean.setBus(endpoint.getBus());
            cfBean.setServiceClass(Class.forName(endpoint.getProperty(CxfConstants.SEI)));
            client = cfBean.create();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client.getConduit().close();
            client = null;
        }

        super.doStop();
    }
}


