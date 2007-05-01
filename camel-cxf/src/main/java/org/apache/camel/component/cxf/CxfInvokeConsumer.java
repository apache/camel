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
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.message.Message;

/**
 * A consumer of exchanges for a service in CXF
 *
 * @version $Revision$
 */
public class CxfInvokeConsumer extends DefaultConsumer<CxfExchange> {
    protected CxfInvokeEndpoint cxfEndpoint;
    private ServerImpl server;

    public CxfInvokeConsumer(CxfInvokeEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.cxfEndpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // TODO we need to add custom cxf message observer and wire the
        // incomingCxfMessage method.  Also, custom cxf interceptors are
        // needed in order to object SOAP/XML message.  Currently, the
        // CXF service invoker will invoke the service class.
        if (server != null) {
            // start a cxf service
            ServerFactoryBean svrBean = new ServerFactoryBean();
            svrBean.setAddress(getEndpoint().getEndpointUri());
            svrBean.setServiceClass(Class.forName(cxfEndpoint.getProperty(CxfConstants.IMPL)));
            svrBean.setBus(cxfEndpoint.getBus());

            server = (ServerImpl) svrBean.create();
            server.start();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (server != null) {
            server.stop();
            server = null;
        }
        super.doStop();
    }

    // TODO this method currently is not being called.
    protected void incomingCxfMessage(Message message) {
        try {
			CxfExchange exchange = cxfEndpoint.createExchange(message);
			getProcessor().process(exchange);
		} catch (Exception e) {
			// TODO: what do do if we are getting processing errors from camel?  Shutdown?
			e.printStackTrace();
		}
    }
}