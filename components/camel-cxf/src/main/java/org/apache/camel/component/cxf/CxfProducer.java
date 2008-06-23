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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cxf.feature.MessageDataFormatFeature;
import org.apache.camel.component.cxf.feature.PayLoadDataFormatFeature;
import org.apache.camel.component.cxf.invoker.CxfClient;
import org.apache.camel.component.cxf.invoker.CxfClientFactoryBean;
import org.apache.camel.component.cxf.invoker.InvokingContext;
import org.apache.camel.component.cxf.invoker.InvokingContextFactory;
import org.apache.camel.component.cxf.spring.CxfEndpointBean;
import org.apache.camel.component.cxf.util.CxfEndpointUtils;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.BindingOperationInfo;

/**
 * Sends messages from Camel into the CXF endpoint
 *
 * @version $Revision$
 */
public class CxfProducer extends DefaultProducer<CxfExchange> {
    private CxfEndpoint endpoint;
    private Client client;
    private DataFormat dataFormat;

    public CxfProducer(CxfEndpoint endpoint) throws CamelException {
        super(endpoint);
        this.endpoint = endpoint;
        dataFormat = CxfEndpointUtils.getDataFormat(endpoint);
        if (dataFormat.equals(DataFormat.POJO)) {
            client = createClientFromClientFactoryBean(null);
        } else {
            // Create CxfClient for message or payload type
            client = createClientForStreamMessage();
        }
    }

    private Client createClientForStreamMessage() throws CamelException {
        CxfClientFactoryBean cfb = new CxfClientFactoryBean();
        Class serviceClass = null;
        if (endpoint.isSpringContextEndpoint()) {
            CxfEndpointBean cxfEndpointBean = endpoint.getCxfEndpointBean();
            serviceClass = cxfEndpointBean.getServiceClass();
        } else {
            if (endpoint.getServiceClass() == null) {
                throw new CamelException("serviceClass setting missing from CXF endpoint configuration");
            }
            try {
                serviceClass = ClassLoaderUtils.loadClass(endpoint.getServiceClass(), this.getClass());
            } catch (ClassNotFoundException e) {
                throw new CamelException(e);
            }
        }

        boolean jsr181Enabled = CxfEndpointUtils.hasWebServiceAnnotation(serviceClass);
        cfb.setJSR181Enabled(jsr181Enabled);

        dataFormat = CxfEndpointUtils.getDataFormat(endpoint);
        List<AbstractFeature> features = new ArrayList<AbstractFeature>();
        if (dataFormat.equals(DataFormat.MESSAGE)) {
            features.add(new MessageDataFormatFeature());
            // features.add(new LoggingFeature());
        } else if (dataFormat.equals(DataFormat.PAYLOAD)) {
            features.add(new PayLoadDataFormatFeature());
            // features.add(new LoggingFeature());
        }
        cfb.setFeatures(features);

        return createClientFromClientFactoryBean(cfb);
    }

    // If cfb is null, we will try to find the right cfb to use.
    private Client createClientFromClientFactoryBean(ClientFactoryBean cfb) throws CamelException {
        Bus bus = null;
        if (endpoint.getApplicationContext() != null) {
            SpringBusFactory bf = new SpringBusFactory(endpoint.getApplicationContext());
            bus = bf.createBus();
            if (CxfEndpointUtils.getSetDefaultBus(endpoint)) {
                BusFactory.setDefaultBus(bus);
            }
        } else {
            // now we just use the default bus here
            bus = BusFactory.getDefaultBus();
        }
        if (endpoint.isSpringContextEndpoint()) {
            CxfEndpointBean cxfEndpointBean = endpoint.getCxfEndpointBean();
            if (cfb == null) {
                cfb = CxfEndpointUtils.getClientFactoryBean(cxfEndpointBean.getServiceClass());
            }
            endpoint.configure(cfb);

        } else { // set up the clientFactoryBean by using URI information
            if (null != endpoint.getServiceClass()) {
                try {
                    // We need to choose the right front end to create the
                    // clientFactoryBean
                    Class serviceClass = ClassLoaderUtils.loadClass(endpoint.getServiceClass(), this
                        .getClass());
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
                    // Throw an exception indicating insufficient endpoint info
                    throw new CamelException("Not enough information to create a CXF endpoint. (Provide WSDL url or service class name.)");
                }
            }
            if (endpoint.getServiceName() != null) {
                cfb.setServiceName(CxfEndpointUtils.getServiceName(endpoint));
            }
            if (endpoint.getPortName() != null) {
                cfb.setEndpointName(CxfEndpointUtils.getPortName(endpoint));

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
        exchange.copyFrom(cxfExchange);

    }

    public void process(CxfExchange exchange) {
        Message inMessage = CxfBinding.createCxfMessage(exchange);
        exchange.setProperty(CxfExchange.DATA_FORMAT, dataFormat);
        try {
            if (dataFormat.equals(DataFormat.POJO)) {
                // InputStream is = m.getContent(InputStream.class);
                // now we just deal with the POJO invocations
                List parameters = inMessage.getContent(List.class);
                if (parameters == null) {
                    parameters = new ArrayList();
                }
                String operationName = (String)inMessage.get(CxfConstants.OPERATION_NAME);
                String operationNameSpace = (String)inMessage.get(CxfConstants.OPERATION_NAMESPACE);
                // Get context from message
                Map<String, Object> context = new HashMap<String, Object>();
                Map<String, Object> responseContext = CxfBinding.propogateContext(inMessage, context);
                Message response = new MessageImpl();
                if (operationName != null) {
                    // we need to check out the operation Namespace
                    try {
                        Object[] result = null;
                        // call for the client with the parameters
                        result = invokeClient(operationNameSpace, operationName, parameters, context);
                        response.setContent(Object[].class, result);
                        // copy the response context to the response
                        CxfBinding.storeCXfResponseContext(response, responseContext);
                        CxfBinding.storeCxfResponse(exchange, response);
                    } catch (Exception ex) {
                        response.setContent(Exception.class, ex);
                        CxfBinding.storeCxfFault(exchange, response);
                    }
                } else {
                    throw new RuntimeCamelException("Can't find the operation name in the message!");
                }
            } else {
                // get the invocation context
                org.apache.cxf.message.Exchange ex = exchange.getExchange();
                if (ex == null) {
                    ex = (org.apache.cxf.message.Exchange)exchange.getProperty(CxfConstants.CXF_EXCHANGE);
                    exchange.setExchange(ex);
                }
                if (ex == null) {
                    ex = new ExchangeImpl();
                    exchange.setExchange(ex);
                }
                assert ex != null;
                InvokingContext invokingContext = ex.get(InvokingContext.class);
                if (invokingContext == null) {
                    invokingContext = InvokingContextFactory.createContext(dataFormat);
                    ex.put(InvokingContext.class, invokingContext);
                }
                Map<Class, Object> params = invokingContext.getRequestContent(inMessage);
                // invoke the stream message with the exchange context
                CxfClient cxfClient = (CxfClient)client;
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
                // invoke the message prepare the context
                Map<String, Object> context = new HashMap<String, Object>();
                Map<String, Object> responseContext = CxfBinding.propogateContext(inMessage, context);
                try {
                    Object result = cxfClient.dispatch(params, context, ex);
                    ex.setOutMessage(response);
                    invokingContext.setResponseContent(response, result);
                    // copy the response context to the response
                    CxfBinding.storeCXfResponseContext(response, responseContext);
                    CxfBinding.storeCxfResponse(exchange, response);
                } catch (Exception e) {
                    response.setContent(Exception.class, e);
                    CxfBinding.storeCxfFault(exchange, response);
                }
            }
        } catch (Exception e) {
            // TODO add the fault message handling work
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

    private Object[] invokeClient(String operationNameSpace, String operationName, List parameters, Map<String, Object> context) throws Exception {

        QName operationQName = null;
        if (operationNameSpace == null) {
            operationQName = new QName(client.getEndpoint().getService().getName().getNamespaceURI(), operationName);
        } else {
            operationQName = new QName(operationNameSpace, operationName);
        }
        BindingOperationInfo op = client.getEndpoint().getEndpointInfo().getBinding().getOperation(operationQName);
        if (op == null) {
            throw new RuntimeCamelException("No operation found in the CXF client, the operation is " + operationQName);
        }
        if (!endpoint.isWrapped()) {
            if (op.isUnwrappedCapable()) {
                op = op.getUnwrappedOperation();
            }
        }
        Object[] result = client.invoke(op, parameters.toArray(), context);

        return result;
    }

}
