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
package org.apache.camel.component.cxf;

import javax.xml.namespace.QName;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.message.Message;


/**
 * Defines the <a href="http://activemq.apache.org/camel/cxf.html">CXF Endpoint</a>
 * 
 * @version $Revision$
 */
public class CxfEndpoint extends DefaultEndpoint<CxfExchange> {    
    private final CxfComponent component;
    private final String address;
    private String wsdlURL;
    private String serviceClass;
    private CxfBinding binding;
    private QName portName;
    private QName serviceName;
    private boolean inOut = true;
    private boolean invoker = true;

    public CxfEndpoint(String uri, String address, CxfComponent component) {
        super(uri, component);
        this.component = component;        
        this.address = address;
    }
        
    public Producer<CxfExchange> createProducer() throws Exception {
        return new CxfProducer(this);
    }

    public Consumer<CxfExchange> createConsumer(Processor processor) throws Exception {
        return new CxfConsumer(this, processor);
    }

    public CxfExchange createExchange() {
        return new CxfExchange(getContext(), getDefaultPattern(), getBinding());
    }

    public CxfExchange createExchange(ExchangePattern pattern) {
        return new CxfExchange(getContext(), pattern, getBinding());
    }

    public CxfExchange createExchange(Message inMessage) {
        return new CxfExchange(getContext(), getDefaultPattern(), getBinding(), inMessage);
    }
    
    public boolean isInvoker() {
        return invoker;
    }
    
    public void setInvoker(boolean invoker) {
        this.invoker = invoker;
    }
    
    public String getAddress() {
    	return address;
    }
    
    public String getWsdlURL() {
    	return wsdlURL;
    }
    
    public void setWsdlURL(String url) {
        wsdlURL = url;
    }
    
    public String getServiceClass() {
    	return serviceClass;
    }
    
    public void setServiceClass(String className) {        
        serviceClass = className;
    }
    
    public void setPortName(QName port) {
        portName = port;
    }
    
    public void setServiceName(QName service) {
        serviceName = service;
    }
    
    public QName getPortName(){
        return portName;
    }
    
    public QName getServiceName() {
        return serviceName;
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

   
    public CxfComponent getComponent() {
        return component;
    }

    public boolean isSingleton() {
        return true;
    }
    
    

}
