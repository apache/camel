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
package org.apache.camel.component.cxf.cxfbean;

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.component.cxf.transport.CamelDestination;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.Bus;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.ConduitInitiator;

/**
 * A CXF transport {@link org.apache.cxf.transport.Destination} that listens 
 * Camel {@link Exchange} from an associated {@link CxfBeanEndpoint}.
 *  
 * @version $Revision$
 */
public class CxfBeanDestination extends CamelDestination implements Processor {
    private static final Log LOG = LogFactory.getLog(CxfBeanDestination.class);
    private CxfBeanComponent cxfBeanComponent;
    private CxfBeanEndpoint endpoint;

    public CxfBeanDestination(CxfBeanComponent cxfBeanComponent, Bus bus,
            ConduitInitiator conduitInitiator,
            EndpointInfo endpointInfo) throws IOException {
        super(null, bus, conduitInitiator, endpointInfo);
        this.cxfBeanComponent = cxfBeanComponent;
    }

    @Override
    public void activate() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Activating CxfBeanDestination " + getCamelDestinationUri());
        }

        endpoint = cxfBeanComponent.getEndpoint(getCamelDestinationUri());
        
        if (endpoint == null) {
            LOG.error("Failed to find endpoint " + getCamelDestinationUri());
            return;
        }
            
        endpoint.setProcessor(this);
    }

    @Override
    public void deactivate() {
    }

    public void process(Exchange camelExchange) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received request : " + camelExchange);
        }
        
        org.apache.cxf.message.Message cxfMessage = 
            endpoint.getCxfBeanBinding().createCxfMessageFromCamelExchange(camelExchange,
                    endpoint.getHeaderFilterStrategy());
                      
        cxfMessage.put(CxfConstants.CAMEL_EXCHANGE, camelExchange);
        ((MessageImpl)cxfMessage).setDestination(this);

        // Handling the incoming message
        // The response message will be send back by the outgoing chain
        incomingObserver.onMessage(cxfMessage);
    }

}
