/*
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

import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.component.cxf.common.UnitOfWorkCloserInterceptor;
import org.apache.camel.component.cxf.util.CxfUtils;
import org.apache.camel.support.DefaultConsumer;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.MessageObserver;

/**
 * A Consumer of exchanges for a JAXRS service in CXF. CxfRsConsumer acts a CXF service to receive REST requests,
 * convert them to a normal java object invocation, and forward them to Camel route for processing. It is also
 * responsible for converting and sending back responses to CXF client.
 */
public class CxfRsConsumer extends DefaultConsumer implements Suspendable {
    private Server server;

    public CxfRsConsumer(CxfRsEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
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
        // setup the UnitOfWorkCloserInterceptor for OneWayMessageProcessor
        svrBean.getInInterceptors().add(new UnitOfWorkCloserInterceptor(Phase.POST_INVOKE, true));
        // close the UnitOfWork normally
        svrBean.getOutInterceptors().add(new UnitOfWorkCloserInterceptor());
        // close the UnitOfWork in case of Fault
        svrBean.getOutFaultInterceptors().add(new UnitOfWorkCloserInterceptor());

        Server server = svrBean.create();

        final MessageObserver originalOutFaultObserver = server.getEndpoint().getOutFaultObserver();
        //proxy OutFaultObserver so we can close org.apache.camel.spi.UnitOfWork in case of error
        server.getEndpoint().setOutFaultObserver(message -> {
            CxfUtils.closeCamelUnitOfWork(message);
            originalOutFaultObserver.onMessage(message);
        });

        return server;
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
