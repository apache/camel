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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.service.invoker.Invoker;
import org.apache.cxf.service.model.BindingOperationInfo;

/**
 * A Consumer of exchanges for a service in CXF.  CxfConsumer acts a CXF
 * service to receive requests, convert them, and forward them to Camel 
 * route for processing. It is also responsible for converting and sending
 * back responses to CXF client.
 *
 * @version $Revision$
 */
public class CxfConsumer extends DefaultConsumer {
    private static final Log LOG = LogFactory.getLog(CxfConsumer.class);
    private Server server;

    public CxfConsumer(CxfEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);
        
        // create server
        ServerFactoryBean svrBean = endpoint.createServerFactoryBean();
        svrBean.setInvoker(new Invoker() {

            // we receive a CXF request when this method is called
            public Object invoke(Exchange cxfExchange, Object o) {
                
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Received CXF Request: " + cxfExchange);
                }
                
                // get CXF binding
                CxfEndpoint endpoint = (CxfEndpoint)getEndpoint();
                CxfBinding binding = endpoint.getCxfBinding();

                // create a Camel exchange
                org.apache.camel.Exchange camelExchange = endpoint.createExchange();
                
                BindingOperationInfo boi = cxfExchange.get(BindingOperationInfo.class);
                if (boi != null) {
                    camelExchange.setProperty(BindingOperationInfo.class.getName(), boi);
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Set exchange property: BindingOperationInfo: " + boi);
                    }
                }
                
                // set data format mode in Camel exchange
                DataFormat dataFormat = endpoint.getDataFormat();
                camelExchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, dataFormat);   
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Set Exchange property: " + DataFormat.class.getName() 
                            + "=" + dataFormat);
                }
                
                // bind the CXF request into a Camel exchange
                binding.populateExchangeFromCxfRequest(cxfExchange, camelExchange);
                                
                // send Camel exchange to the target processor
                try {
                    getProcessor().process(camelExchange);
                } catch (Exception e) {
                    throw new Fault(e);
                }
                
                // check failure
                if (camelExchange.isFailed()) {
                    Throwable t = (Throwable)camelExchange.getFault().getBody();
                    if (t instanceof Fault) {
                        throw (Fault)t;
                    } else if (t == null) {
                        t = camelExchange.getException();
                    }
                    throw new Fault(t);
                }
                
                // bind the Camel response into a CXF response
                if (camelExchange.getPattern().isOutCapable()) {
                    binding.populateCxfResponseFromExchange(camelExchange, cxfExchange);
                } 
                
                // response should have been set in outMessage's content
                return null;
            }
            
        });
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
    
    public Server getServer() {
        return server;
    }
    
}
