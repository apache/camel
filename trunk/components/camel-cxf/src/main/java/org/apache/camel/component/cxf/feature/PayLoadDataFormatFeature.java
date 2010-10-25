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
package org.apache.camel.component.cxf.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import org.apache.camel.component.cxf.interceptors.ConfigureDocLitWrapperInterceptor;
import org.apache.camel.component.cxf.interceptors.RemoveClassTypeInterceptor;
import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.interceptor.ClientFaultConverter;

/**
 * This feature just setting up the CXF endpoint interceptor for handling the
 * Message in PAYLOAD data format
 */
public class PayLoadDataFormatFeature extends AbstractDataFormatFeature {
    private static final Logger LOG = LogUtils.getL7dLogger(PayLoadDataFormatFeature.class);    
    private static final Collection<Class> REMOVING_FAULT_IN_INTERCEPTORS;

    static {
        REMOVING_FAULT_IN_INTERCEPTORS = new ArrayList<Class>();
        REMOVING_FAULT_IN_INTERCEPTORS.add(ClientFaultConverter.class);
    }
    
    @Override
    public void initialize(Client client, Bus bus) {
        removeFaultInInterceptorFromClient(client);
        client.getEndpoint().getBinding().getInInterceptors().add(new ConfigureDocLitWrapperInterceptor(true));
        client.getEndpoint().getBinding().getInInterceptors().add(new RemoveClassTypeInterceptor());
    }

    @Override
    public void initialize(Server server, Bus bus) {               
        server.getEndpoint().getBinding().getInInterceptors().add(new ConfigureDocLitWrapperInterceptor(true));
        server.getEndpoint().getBinding().getInInterceptors().add(new RemoveClassTypeInterceptor());
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
    
    private void removeFaultInInterceptorFromClient(Client client) {
        removeInterceptors(client.getInFaultInterceptors(), REMOVING_FAULT_IN_INTERCEPTORS);
        removeInterceptors(client.getEndpoint().getService().getInFaultInterceptors(), REMOVING_FAULT_IN_INTERCEPTORS);
        removeInterceptors(client.getEndpoint().getInFaultInterceptors(), REMOVING_FAULT_IN_INTERCEPTORS);
        removeInterceptors(client.getEndpoint().getBinding().getInFaultInterceptors(), REMOVING_FAULT_IN_INTERCEPTORS);        
    }

}
