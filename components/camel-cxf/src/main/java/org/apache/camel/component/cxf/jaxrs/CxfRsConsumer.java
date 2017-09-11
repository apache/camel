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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.OutFaultChainInitiatorObserver;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.MessageObserver;

/**
 * A Consumer of exchanges for a JAXRS service in CXF.  CxfRsConsumer acts a CXF
 * service to receive REST requests, convert them to a normal java object invocation,
 * and forward them to Camel route for processing. 
 * It is also responsible for converting and sending back responses to CXF client. 
 */
public class CxfRsConsumer extends DefaultConsumer {
    private Server server;

    public CxfRsConsumer(CxfRsEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        server = createServer();
    }

    protected Server createServer() {
        CxfRsEndpoint endpoint = (CxfRsEndpoint) getEndpoint();
        CxfRsInvoker cxfRsInvoker = new CxfRsInvoker(endpoint, this);
        JAXRSServerFactoryBean svrBean = endpoint.createJAXRSServerFactoryBean();
        Bus bus = endpoint.getBus();

        // We need to apply the bus setting from the CxfRsEndpoint which does not use the default bus
        if (bus != null) {
            svrBean.setBus(bus);

        }

        svrBean.setInvoker(cxfRsInvoker);

        svrBean.getOutInterceptors().add(new UnitOfWorkCloserInterceptor());


        Server server = svrBean.create();

        final MessageObserver originalOutFaultObserver = server.getEndpoint().getOutFaultObserver();
        //proxy OutFaultObserver so we can close org.apache.camel.spi.UnitOfWork in case of error
        server.getEndpoint().setOutFaultObserver(message -> {
            org.apache.cxf.message.Exchange cxfExchange = null;
            if ((cxfExchange = message.getExchange()) != null) {
                org.apache.camel.Exchange exchange = cxfExchange.get(org.apache.camel.Exchange.class);
                if (exchange != null) {
                    doneUoW(exchange);
                }
            }
            originalOutFaultObserver.onMessage(message);
        });

        return server;
    }

    //closes UnitOfWork in good case
    private class UnitOfWorkCloserInterceptor extends AbstractPhaseInterceptor<Message> {
        public UnitOfWorkCloserInterceptor() {
            super(Phase.POST_LOGICAL_ENDING);
        }

        @Override
        public void handleMessage(Message message) throws Fault {
            org.apache.cxf.message.Exchange cxfExchange = null;
            if ((cxfExchange = message.getExchange()) != null) {
                org.apache.camel.Exchange exchange = cxfExchange.get(org.apache.camel.Exchange.class);
                if (exchange != null) {
                    doneUoW(exchange);
                }
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (server == null) {
            server = createServer();
        }
        server.start();
    }

    @Override
    protected void doStop() throws Exception {
        if (server != null) {
            server.stop();
            server.destroy();
            server = null;
        }
        super.doStop();
    }
    
    public Server getServer() {
        return server;
    }

}
