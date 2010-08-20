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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.xml.ws.WebFault;

import org.w3c.dom.Element;

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
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
                DataFormat dataFormat = endpoint.getDataFormat();

                BindingOperationInfo boi = cxfExchange.getBindingOperationInfo();
                // make sure the "boi" is remained as wrapped in PAYLOAD mode
                if (dataFormat == DataFormat.PAYLOAD && boi.isUnwrapped()) {
                    boi = boi.getWrappedOperation();
                    cxfExchange.put(BindingOperationInfo.class, boi);
                }
                
                if (boi != null) {
                    camelExchange.setProperty(BindingOperationInfo.class.getName(), boi);
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Set exchange property: BindingOperationInfo: " + boi);
                    }
                }
                
                // set data format mode in Camel exchange
                camelExchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, dataFormat);   
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Set Exchange property: " + DataFormat.class.getName() 
                            + "=" + dataFormat);
                }
                
                camelExchange.setProperty(Message.MTOM_ENABLED, String.valueOf(endpoint.isMtomEnabled()));
                
                // bind the CXF request into a Camel exchange
                binding.populateExchangeFromCxfRequest(cxfExchange, camelExchange);
                
                // extract the javax.xml.ws header
                Map<String, Object> context = new HashMap<String, Object>();
                binding.extractJaxWsContext(cxfExchange, context);                 
                // send Camel exchange to the target processor
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing +++ START +++");
                }
                try {
                    getProcessor().process(camelExchange);
                } catch (Exception e) {
                    throw new Fault(e);
                }
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing +++ END +++");
                }
                checkFailure(camelExchange);
              
                // bind the Camel response into a CXF response
                if (camelExchange.getPattern().isOutCapable()) {
                    binding.populateCxfResponseFromExchange(camelExchange, cxfExchange);
                }
                
                // check failure again as fault could be discovered by converter
                checkFailure(camelExchange);

                // copy the headers javax.xml.ws header back
                binding.copyJaxWsContext(cxfExchange, context);
                // response should have been set in outMessage's content
                return null;
            }

            private void checkFailure(org.apache.camel.Exchange camelExchange) throws Fault {
                final Throwable t;
                if (camelExchange.isFailed()) {
                    t = (camelExchange.hasOut() && camelExchange.getOut().isFault()) ? camelExchange.getOut()
                        .getBody(Throwable.class) : camelExchange.getException();
                    if (t instanceof Fault) {
                        throw (Fault)t;
                    } else if (t != null) {                        
                        // This is not a CXF Fault. Build the CXF Fault manuallly.
                        Fault fault = new Fault(t);
                        if (fault.getMessage() == null) {
                            // The Fault has no Message. This is the case if t had
                            // no message, for
                            // example was a NullPointerException.
                            fault.setMessage(t.getClass().getSimpleName());
                        }
                        WebFault faultAnnotation = t.getClass().getAnnotation(WebFault.class);
                        Object faultInfo = null;
                        try {
                            Method method = t.getClass().getMethod("getFaultInfo", new Class[0]);
                            faultInfo = method.invoke(t, new Object[0]);
                        } catch (Exception e) {
                            // do nothing here                            
                        }
                        if (faultAnnotation != null && faultInfo == null) {
                            // t has a JAX-WS WebFault annotation, which describes
                            // in detail the Web Service Fault that should be thrown. Add the
                            // detail.
                            Element detail = fault.getOrCreateDetail();
                            Element faultDetails = detail.getOwnerDocument()
                                .createElementNS(faultAnnotation.targetNamespace(), faultAnnotation.name());
                            detail.appendChild(faultDetails);
                        }

                        throw fault;
                    }

                }
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
