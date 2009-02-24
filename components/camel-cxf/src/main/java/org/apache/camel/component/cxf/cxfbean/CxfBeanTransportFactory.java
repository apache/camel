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

import org.apache.camel.component.cxf.transport.CamelTransportFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.Destination;

/**
 * CXF Bean TransportFactory that overrides CamelTransportFactory to create
 * a specific Destination (@link CxfBeanDestination}.
 * 
 * @version $Revision$
 */
public class CxfBeanTransportFactory extends CamelTransportFactory  {
    public static final String TRANSPORT_ID = "http://cxf.apache.org/transports/camel/cxfbean";
    private static final Log LOG = LogFactory.getLog(CxfBeanTransportFactory.class);
    private CxfBeanComponent cxfBeanComponent;
 
    @Override
    public Destination getDestination(EndpointInfo endpointInfo) throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Create CxfBeanDestination: " + endpointInfo);
        }
        
        // lookup endpoint from component instead of CamelContext because it may not
        // be added to the CamelContext yet.
        return new CxfBeanDestination(cxfBeanComponent, getBus(), this, endpointInfo);
    }

    /**
     * @param cxfBeanComponent
     */
    public void setCxfBeanComponent(CxfBeanComponent cxfBeanComponent) {
        this.cxfBeanComponent = cxfBeanComponent;
        
    }
    
}
