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
package org.apache.camel.component.jbi;

import javax.jbi.component.ComponentContext;
import javax.jbi.messaging.MessageExchange.Role;
import javax.xml.namespace.QName;
import org.apache.servicemix.common.ExchangeProcessor;
import org.apache.servicemix.jbi.container.JBIContainer;
import org.apache.servicemix.jbi.framework.ComponentContextImpl;

/**
 * The endpoint in the service engine
 * @version $Revision: 426415 $
 */
public class CamelServiceEngineEndpoint extends org.apache.servicemix.common.Endpoint {

    private static final QName SERVICE_NAME = new QName("http://camel.servicemix.org", "CamelEndpointComponent");
    
    private CamelEndpointComponent camelEndpointComponent;
    
    public CamelServiceEngineEndpoint(JbiEndpoint jbiEndpoint) {
        this.camelEndpointComponent = new CamelEndpointComponent(jbiEndpoint);
        this.service = SERVICE_NAME;
        this.endpoint=jbiEndpoint.getEndpointUri();
    }
    
    public Role getRole() {
        throw new UnsupportedOperationException();
    }

    public void activate() throws Exception {
        getContainer().activateComponent(camelEndpointComponent,camelEndpointComponent.getName());
    }

    public void deactivate() throws Exception {
        getContainer().deactivateComponent(camelEndpointComponent.getName());
    }

    public ExchangeProcessor getProcessor() {
        throw new UnsupportedOperationException();
    }

    public JBIContainer getContainer() {
        ComponentContext context = getServiceUnit().getComponent().getComponentContext();
        if( context instanceof ComponentContextImpl ) {
            return ((ComponentContextImpl) context).getContainer();
        }
        throw new IllegalStateException("LwContainer component can only be deployed in ServiceMix");
    }

}
