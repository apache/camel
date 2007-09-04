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

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.transport.Conduit;

import java.net.MalformedURLException;

/**
 * Sends messages from Camel into the CXF endpoint
 * 
 * @version $Revision$
 */
public class CxfProducer extends DefaultProducer {
    private CxfEndpoint endpoint;
    private Client client;    
    private Conduit conduit;
    

    public CxfProducer(CxfEndpoint endpoint) throws MalformedURLException {
        super(endpoint);
        this.endpoint = endpoint;
        client = createClient();
    }
    
    private Client createClient() throws MalformedURLException {
        Bus bus = BusFactory.getDefaultBus();
        // setup the ClientFactoryBean with endpoint
        ClientFactoryBean cfb = new ClientFactoryBean();
        cfb.setBus(bus);
        cfb.setAddress(endpoint.getAddress());
        if (null != endpoint.getServiceClass()) {            
            cfb.setServiceClass(ObjectHelper.loadClass(endpoint.getServiceClass()));
        }
        if (null != endpoint.getWsdlURL()) {
            cfb.setWsdlURL(endpoint.getWsdlURL());
        }       
        // there may other setting work
        // create client 
        return cfb.create();
    }
   
    public void process(Exchange exchange) {
        CxfExchange cxfExchange = endpoint.createExchange(exchange);
        process(cxfExchange);
    }

    public void process(CxfExchange exchange) {
        try {
            CxfBinding binding = endpoint.getBinding();
            MessageImpl m = binding.createCxfMessage(exchange);
            //InputStream is = m.getContent(InputStream.class);
            // now we just deal with the POJO invocations 
            List paraments = m.getContent(List.class);
            Message response = new MessageImpl();            
            if (paraments != null) {
            	String operation = (String)paraments.get(0);
            	Object[] args = new Object[paraments.size()-1];
            	for(int i = 0 ; i < paraments.size()-1 ; i++) {            		
            		args[i] = paraments.get(i+1);
            	}
            	// now we just deal with the invoking the paraments
            	Object[] result = client.invoke(operation, args);                
            	response.setContent(Object[].class, result);
                binding.storeCxfResponse(exchange, response);
            }          	
            
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }   
                
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
                
        client = createClient();
        conduit = client.getConduit();
        
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();        
    }

}
