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

import java.net.URI;

import org.apache.camel.Processor;
import org.apache.camel.component.cxf.spring.CxfEndpointBean;
import org.apache.camel.component.cxf.util.CxfEndpointUtils;
import org.apache.camel.component.cxf.util.UriUtils;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.MessageObserver;


/**
 * A consumer of exchanges for a service in CXF
 * 
 * @version $Revision$
 */
public class CxfConsumer extends DefaultConsumer<CxfExchange> {
    private CxfEndpoint endpoint;    
    private Server server;
    

    public CxfConsumer(CxfEndpoint endpoint, Processor processor) throws Exception {
       
        super(endpoint, processor);        
        this.endpoint = endpoint;        
        
        try {
            // now we just use the default bus here   
            Bus bus = BusFactory.getDefaultBus();
            ServerFactoryBean svrBean = null;
            if (endpoint.isSpringContextEndpoint()) {
                CxfEndpointBean endpointBean = endpoint.getCxfEndpointBean();
                svrBean = CxfEndpointUtils.getServerFactoryBean(endpointBean.getServiceClass());
                endpoint.configure(svrBean);
                //Need to set the service name and endpoint name to the ClientFactoryBean's service factory
                // to walk around the issue of setting EndpointName and ServiceName
                CxfEndpointBean cxfEndpointBean = endpoint.getCxfEndpointBean();
                if (cxfEndpointBean.getServiceName() != null) {
                    svrBean.getServiceFactory().setServiceName(cxfEndpointBean.getServiceName());
                } 
                if (cxfEndpointBean.getEndpointName() != null) {
                    svrBean.getServiceFactory().setEndpointName(cxfEndpointBean.getEndpointName());
                } 
                
            } else { // setup the serverFactoryBean with the URI paraments           
                Class serviceClass = ClassLoaderUtils.loadClass(endpoint.getServiceClass(), this.getClass()); 
                svrBean = CxfEndpointUtils.getServerFactoryBean(serviceClass);                           
                svrBean.setAddress(endpoint.getAddress());
                svrBean.setServiceClass(serviceClass);
                if (endpoint.getServiceName() != null) {
                    svrBean.getServiceFactory().setServiceName(CxfEndpointUtils.getServiceName(endpoint));                
                }
                if (endpoint.getPortName() != null) {
                    svrBean.getServiceFactory().setEndpointName(CxfEndpointUtils.getPortName(endpoint));
                }    
                if (endpoint.getWsdlURL() != null) {                
                    svrBean.setWsdlURL(endpoint.getWsdlURL());
                }
            }
            DataFormat dataFormat = CxfEndpointUtils.getDataFormat(endpoint);
            if (dataFormat.equals(DataFormat.POJO)) {
                svrBean.setInvoker(new CamelInvoker(this));
            }
            svrBean.setBus(bus);
            svrBean.setStart(false);
            server = svrBean.create();            
            if (!dataFormat.equals(DataFormat.POJO)) {
                CxfMessageObserver observer = new CxfMessageObserver(this, server.getEndpoint(), bus , dataFormat);
                //set the message observer for the Message and PayLoad mode message 
                ServerImpl serverImpl = (ServerImpl)server;
                serverImpl.setMessageObserver(observer);
            } 
            
        } catch (Exception ex) {
            // create Consumer endpoint failed
            ex.printStackTrace();
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();        
        
        server.start();
    }

    @Override
    protected void doStop() throws Exception {
        server.stop();
        super.doStop();
    }
    
    public CxfEndpoint getEndpoint() {
        return endpoint;
    }

    
}
