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

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.cxf.endpoint.Server;
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
    private Destination destination;

    public CxfConsumer(CxfEndpoint endpoint, Processor processor) throws ClassNotFoundException {
       
        super(endpoint, processor);
        System.out.println(processor.toString());
        this.endpoint = endpoint;
        //we setup the interceptors by the endpoint configuration
        //create server here, now we just use the simple front-end        
        ServerFactoryBean svrBean = new ServerFactoryBean();
        Class serviceClass = Class.forName(endpoint.getServiceClass());        
        svrBean.setAddress(endpoint.getAddress());
        svrBean.setServiceClass(serviceClass);
        if (endpoint.isInvoker()) {
            System.out.println("setup the invoker ");
            svrBean.setInvoker(new CamelInvoker(this));
        }    
        svrBean.setStart(false);
        server = svrBean.create();
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

    
}
