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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.cxf.BusException;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.local.LocalTransportFactory;

/**
 * Defines the <a href="http://activemq.apache.org/camel/cxf.html">CXF Endpoint</a>
 *
 * @version $Revision$
 */
public class CxfEndpoint extends DefaultEndpoint<CxfExchange> {
    private CxfBinding binding;
    private final CxfComponent component;
    private final EndpointInfo endpointInfo;
    private boolean inOut = true;

    public CxfEndpoint(String uri, CxfComponent component, EndpointInfo endpointInfo) {
        super(uri, component);
        this.component = component;
        this.endpointInfo = endpointInfo;
    }

    public Producer<CxfExchange> createProducer() throws Exception {
        return new CxfProducer(this, getLocalTransportFactory());
    }

    public Consumer<CxfExchange> createConsumer(Processor processor) throws Exception {
        return new CxfConsumer(this, processor, getLocalTransportFactory());
    }

    public CxfExchange createExchange() {
        return new CxfExchange(getContext(), getBinding());
    }

    public CxfExchange createExchange(Message inMessage) {
        return new CxfExchange(getContext(), getBinding(), inMessage);
    }

    public CxfBinding getBinding() {
        if (binding == null) {
            binding = new CxfBinding();
        }
        return binding;
    }

    public void setBinding(CxfBinding binding) {
        this.binding = binding;
    }

    public boolean isInOut() {
        return inOut;
    }

    public void setInOut(boolean inOut) {
        this.inOut = inOut;
    }

    public LocalTransportFactory getLocalTransportFactory() throws BusException {
        return component.getLocalTransportFactory();
    }

    public EndpointInfo getEndpointInfo() {
        return endpointInfo;
    }

    public CxfComponent getComponent() {
        return component;
    }
    
	public boolean isSingleton() {
		return true;
	}

}
