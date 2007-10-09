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

import java.util.List;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cxf.invoker.CxfClient;
import org.apache.camel.component.cxf.invoker.CxfClientFactoryBean;
import org.apache.camel.component.cxf.invoker.InvokingContext;
import org.apache.camel.component.cxf.spring.CxfEndpointBean;
import org.apache.camel.component.cxf.util.CxfEndpointUtils;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.Binding;
import org.apache.cxf.binding.BindingFactory;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.transport.Conduit;

import java.io.InputStream;
import java.net.MalformedURLException;

/**
 * Sends messages from Camel into the CXF endpoint
 * 
 * @version $Revision$
 */
public class CxfProducer extends DefaultProducer <CxfExchange> {
    private CxfEndpoint endpoint;
    private Client client;
    private DataFormat dataFormat;
    
    

    public CxfProducer(CxfEndpoint endpoint) throws CamelException {
        super(endpoint);
        this.endpoint = endpoint;
        dataFormat = CxfEndpointUtils.getDataFormat(endpoint);
        if (dataFormat.equals(DataFormat.POJO)) {
            client = createClientFormClientFactoryBean(null);
        } else {
            // create CxfClient for message
            client = createClientForStreamMessge();           
        }
    }
    
    private Client createClientForStreamMessge() throws CamelException {
        CxfClientFactoryBean cfb = new CxfClientFactoryBean();
        if (null != endpoint.getServiceClass()) {
            try {
                Class serviceClass = ClassLoaderUtils.loadClass(endpoint.getServiceClass(), this.getClass());
                boolean jsr181Enabled = CxfEndpointUtils.hasWebServiceAnnotation(serviceClass);
                cfb.setJSR181Enabled(jsr181Enabled);
            } catch (ClassNotFoundException e) {
                throw new CamelException(e);
            }
        }
        return createClientFormClientFactoryBean(cfb);
    }
   
    //If cfb is null ,we will try to find a right cfb to use.    
    private Client createClientFormClientFactoryBean(ClientFactoryBean cfb) throws CamelException {              
        Bus bus = BusFactory.getDefaultBus();
        if (endpoint.isSpringContextEndpoint()) {
            CxfEndpointBean endpointBean = endpoint.getCxfEndpointBean();
            if (cfb == null) {
                cfb = CxfEndpointUtils.getClientFactoryBean(endpointBean.getServiceClass());
            }    
            endpoint.configure(cfb);
            // Need to set the service name and endpoint name to the ClientFactoryBean's service factory
            // to walk around the issue of setting EndpointName and ServiceName
            CxfEndpointBean cxfEndpointBean = endpoint.getCxfEndpointBean();
            if (cxfEndpointBean.getServiceName() != null) {
                cfb.getServiceFactory().setServiceName(cxfEndpointBean.getServiceName());
            } 
            if (cxfEndpointBean.getEndpointName() != null) {
                cfb.getServiceFactory().setEndpointName(cxfEndpointBean.getEndpointName());
            } 
        } else { // set up the clientFactoryBean by using URI information
            if (null != endpoint.getServiceClass()) {
                try {
                    //we need to choice the right front end to create the clientFactoryBean
                    Class serviceClass = ClassLoaderUtils.loadClass(endpoint.getServiceClass(), this.getClass());
                    if (cfb == null) {
                        cfb = CxfEndpointUtils.getClientFactoryBean(serviceClass);
                    } 
                    cfb.setAddress(endpoint.getAddress());
                    if (null != endpoint.getServiceClass()) {            
                        cfb.setServiceClass(ObjectHelper.loadClass(endpoint.getServiceClass()));
                    } 
                    if (null != endpoint.getWsdlURL()) {
                        cfb.setWsdlURL(endpoint.getWsdlURL());
                    }                
                } catch (ClassNotFoundException e) {
                    throw new CamelException(e);
                }
            } else { // we can't see any service class from the endpoint
                if (cfb == null) {
                    cfb = new ClientFactoryBean();
                }    
                if (null != endpoint.getWsdlURL()) {
                    cfb.setWsdlURL(endpoint.getWsdlURL());
                } else {
                    // throw the exception for insufficiency of the endpoint info
                    throw new CamelException("Insufficiency of the endpoint info");
                }
            }
            if (endpoint.getServiceName() != null) {
                cfb.getServiceFactory().setServiceName(CxfEndpointUtils.getServiceName(endpoint));
            }
            if (endpoint.getPortName() != null) {
                cfb.getServiceFactory().setEndpointName(CxfEndpointUtils.getPortName(endpoint));
               
            }    
            if (endpoint.getWsdlURL() != null) {                
                cfb.setWsdlURL(endpoint.getWsdlURL());
            }
        }    
        cfb.setBus(bus);        
        return cfb.create();
    }
   
    public void process(Exchange exchange) {
        CxfExchange cxfExchange = endpoint.createExchange(exchange);
        process(cxfExchange);
    }

    public void process(CxfExchange exchange) {
        CxfBinding cxfBinding = endpoint.getBinding();
        Message inMessage = cxfBinding.createCxfMessage(exchange);
        try {
            if (dataFormat.equals(DataFormat.POJO)) {
                //InputStream is = m.getContent(InputStream.class);
                // now we just deal with the POJO invocations 
                List paraments = inMessage.getContent(List.class);
                String operation = inMessage.getContent(String.class);
                Message response = new MessageImpl();            
                if (operation != null && paraments != null) {                
                    // now we just deal with the invoking the paraments
                    try {
                        Object[] result = client.invoke(operation, paraments.toArray());                
                        response.setContent(Object[].class, result);
                        cxfBinding.storeCxfResponse(exchange, response);
                    } catch (Exception ex) {
                        response.setContent(Exception.class, ex);
                        cxfBinding.storeCxfFault(exchange, response);                        
                    }
                }  
            } else {
                // get the invocation context
                org.apache.cxf.message.Exchange ex = exchange.getExchange();
                InvokingContext invokingContext = ex.get(InvokingContext.class);
                Object params = invokingContext.getRequestContent(inMessage);
                // invoke the stream message with the exchange context
                CxfClient cxfClient = (CxfClient) client;
                // invoke the message
                //TODO need setup the call context here
                //TODO need to handle the one way message
                Object result = cxfClient.dispatch(params, null, ex);
                // need to get the binding object to create the message
                BindingOperationInfo boi = ex.get(BindingOperationInfo.class);
                Message response = null;                
                if (boi == null) {
                    // it should be the raw message                    
                    response = new MessageImpl(); 
                } else {
                    // create the message here
                    Endpoint ep = ex.get(Endpoint.class);                    
                    response = ep.getBinding().createMessage();
                }                
                response.setExchange(ex);
                ex.setOutMessage(response);                
                invokingContext.setResponseContent(response, result);
                cxfBinding.storeCxfResponse(exchange, response);
            }
        } catch (Exception e) {
            //TODO add the falut message handling work
            throw new RuntimeCamelException(e);
        }     
                
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart(); 
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();        
    }

}
