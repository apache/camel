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
package org.apache.camel.component.cxf;

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.InterceptorProvider;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public class TestCxfFeature extends AbstractFeature {

    @Override
    protected void initializeProvider(InterceptorProvider provider, Bus bus) {
            
        if (provider instanceof Client) {
            provider = ((Client)provider).getEndpoint();
        }
        
        provider.getOutInterceptors().add(new EndpointCheckInterceptor());
                
    }
    
    class EndpointCheckInterceptor extends AbstractPhaseInterceptor<Message> {

        EndpointCheckInterceptor() {
            super(Phase.PREPARE_SEND);
        }

        @Override
        public void handleMessage(Message message) throws Fault {
            Exchange ex = message.getExchange();
            
            // This test verifies that the "to" endpoint is not the from endpoint.
            Endpoint endpoint = ex.get(Endpoint.class);
            if ("http://localhost:9003/CamelContext/RouterPort".equals(endpoint.getEndpointInfo().getAddress())) {
                throw new Fault(new Exception("bad endpoint " + endpoint.getEndpointInfo().getAddress()));
            }
            
        }
    }


}
