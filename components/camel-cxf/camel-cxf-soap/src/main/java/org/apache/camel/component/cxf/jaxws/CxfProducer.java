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
package org.apache.camel.component.cxf.jaxws;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.xml.ws.Holder;
import jakarta.xml.ws.handler.MessageContext.Scope;

import javax.xml.namespace.QName;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cxf.common.CxfPayload;
import org.apache.camel.component.cxf.common.DataFormat;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.binding.soap.model.SoapHeaderInfo;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.jaxws.context.WrappedMessageContext;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CxfProducer binds a Camel exchange to a CXF exchange, acts as a CXF client, and sends the request to a CXF to a
 * server. Any response will be bound to Camel exchange.
 */
public class CxfProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CxfProducer.class);

    private Client client;
    private CxfEndpoint endpoint;

    /**
     * Constructor to create a CxfProducer. It will create a CXF client object.
     *
     * @param  endpoint  a CxfEndpoint that creates this producer
     * @throws Exception any exception thrown during the creation of a CXF client
     */
    public CxfProducer(CxfEndpoint endpoint) throws Exception {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        // failsafe as cxf may not ensure the endpoint is started (CAMEL-8956)
        ServiceHelper.startService(endpoint);

        if (client == null) {
            client = endpoint.createClient();
        }

        endpoint.getChainedCxfConfigurer().configureClient(client);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (client != null) {
            // It will help to release the request context map
            client.destroy();
            client = null;
        }
    }

    // As the cxf client async and sync api is implement different,
    // so we don't delegate the sync process call to the async process
    @Override
    public boolean process(Exchange camelExchange, AsyncCallback callback) {
        LOG.trace("Process exchange: {} in an async way.", camelExchange);

        try {
            // create CXF exchange
            ExchangeImpl cxfExchange = new ExchangeImpl();
            // set the Bus on the exchange in case the CXF interceptor need to access it from exchange
            cxfExchange.put(Bus.class, endpoint.getBus());

            // prepare binding operation info
            BindingOperationInfo boi = prepareBindingOperation(camelExchange, cxfExchange);

            Map<String, Object> invocationContext = new HashMap<>();
            Map<String, Object> responseContext = new HashMap<>();
            invocationContext.put(CxfConstants.RESPONSE_CONTEXT, responseContext);
            invocationContext.put(CxfConstants.REQUEST_CONTEXT, prepareRequest(camelExchange, cxfExchange));

            CxfClientCallback cxfClientCallback = new CxfClientCallback(callback, camelExchange, cxfExchange, boi, endpoint);
            // send the CXF async request
            client.invoke(cxfClientCallback, boi, getParams(endpoint, camelExchange),
                    invocationContext, cxfExchange);
            if (boi.getOperationInfo().isOneWay()) {
                callback.done(false);
            }
        } catch (Exception ex) {
            // error occurred before we had a chance to go async
            // so set exception and invoke callback true
            camelExchange.setException(ex);
            callback.done(true);
            return true;
        }
        return false;
    }

    /**
     * This processor binds Camel exchange to a CXF exchange and invokes the CXF client.
     */
    @Override
    public void process(Exchange camelExchange) throws Exception {
        LOG.trace("Process exchange: {} in sync way.", camelExchange);

        // create CXF exchange
        ExchangeImpl cxfExchange = new ExchangeImpl();
        // set the Bus on the exchange in case the CXF interceptor need to access it from exchange
        cxfExchange.put(Bus.class, endpoint.getBus());

        // prepare binding operation info
        BindingOperationInfo boi = prepareBindingOperation(camelExchange, cxfExchange);

        Map<String, Object> invocationContext = new HashMap<>();
        Map<String, Object> responseContext = new HashMap<>();
        invocationContext.put(CxfConstants.RESPONSE_CONTEXT, responseContext);
        invocationContext.put(CxfConstants.REQUEST_CONTEXT, prepareRequest(camelExchange, cxfExchange));

        try {
            // send the CXF request
            client.invoke(boi, getParams(endpoint, camelExchange),
                    invocationContext, cxfExchange);

        } catch (Exception exception) {
            camelExchange.setException(exception);
        } finally {
            // add cookies to the cookie store
            if (endpoint.getCookieHandler() != null) {
                try {
                    Message inMessage = cxfExchange.getInMessage();
                    if (inMessage != null) {
                        Map<String, List<String>> cxfHeaders
                                = CastUtils.cast((Map<?, ?>) inMessage.get(CxfConstants.PROTOCOL_HEADERS));
                        endpoint.getCookieHandler().storeCookies(camelExchange, endpoint.getRequestUri(camelExchange),
                                cxfHeaders);
                    }
                } catch (IOException e) {
                    LOG.warn("Cannot store cookies. This exception is ignored.", e);
                }
            }

            // bind the CXF response to Camel exchange
            if (!boi.getOperationInfo().isOneWay()) {
                endpoint.getCxfBinding().populateExchangeFromCxfResponse(camelExchange, cxfExchange,
                        responseContext);
            }
        }
    }

    protected Map<String, Object> prepareRequest(Exchange camelExchange, org.apache.cxf.message.Exchange cxfExchange)
            throws Exception {

        // create invocation context
        WrappedMessageContext requestContext
                = new WrappedMessageContext(new HashMap<>(), null, Scope.APPLICATION);

        camelExchange.setProperty(Message.MTOM_ENABLED, String.valueOf(endpoint.isMtomEnabled()));

        // set data format mode in exchange
        DataFormat dataFormat = endpoint.getDataFormat();
        camelExchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, dataFormat);
        LOG.trace("Set Camel Exchange property: {}={}", DataFormat.class.getName(), dataFormat);

        if (endpoint.isMergeProtocolHeaders()) {
            camelExchange.setProperty(CxfConstants.CAMEL_CXF_PROTOCOL_HEADERS_MERGED, Boolean.TRUE);
        }

        // set data format mode in the request context
        requestContext.put(DataFormat.class.getName(), dataFormat);

        // don't let CXF ClientImpl close the input stream
        if (dataFormat.dealias() == DataFormat.RAW) {
            cxfExchange.put(Client.KEEP_CONDUIT_ALIVE, true);
            LOG.trace("Set CXF Exchange property: {}={}", Client.KEEP_CONDUIT_ALIVE, true);
        }

        // bind the request CXF exchange
        endpoint.getCxfBinding().populateCxfRequestFromExchange(cxfExchange, camelExchange,
                requestContext);

        // add appropriate cookies from the cookie store to the protocol headers
        if (endpoint.getCookieHandler() != null) {
            try {
                Map<String, List<String>> transportHeaders
                        = CastUtils.cast((Map<?, ?>) requestContext.get(CxfConstants.PROTOCOL_HEADERS));
                boolean added;
                if (transportHeaders == null) {
                    transportHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    added = true;
                } else {
                    added = false;
                }
                transportHeaders
                        .putAll(endpoint.getCookieHandler().loadCookies(camelExchange, endpoint.getRequestUri(camelExchange)));
                if (added && transportHeaders.size() > 0) {
                    requestContext.put(CxfConstants.PROTOCOL_HEADERS, transportHeaders);
                }
            } catch (IOException e) {
                LOG.warn("Cannot load cookies", e);
            }
        }

        // Remove protocol headers from scopes.  Otherwise, response headers can be
        // overwritten by request headers when SOAPHandlerInterceptor tries to create
        // a wrapped message context by the copyScoped() method.
        requestContext.getScopes().remove(CxfConstants.PROTOCOL_HEADERS);

        return requestContext.getWrappedMap();
    }

    private BindingOperationInfo prepareBindingOperation(Exchange camelExchange, org.apache.cxf.message.Exchange cxfExchange) {
        // get binding operation info
        BindingOperationInfo boi = getBindingOperationInfo(camelExchange);
        ObjectHelper.notNull(boi, "BindingOperationInfo");

        // keep the message wrapper in PAYLOAD mode
        if (endpoint.getDataFormat() == DataFormat.PAYLOAD && boi.isUnwrapped()) {
            boi = boi.getWrappedOperation();
            cxfExchange.put(BindingOperationInfo.class, boi);

        }

        // store the original boi in the exchange
        camelExchange.setProperty(BindingOperationInfo.class.getName(), boi);
        LOG.trace("Set exchange property: BindingOperationInfo: {}", boi);

        // Unwrap boi before passing it to make a client call
        if (endpoint.getDataFormat() != DataFormat.PAYLOAD && !endpoint.isWrapped() && boi != null) {
            if (boi.isUnwrappedCapable()) {
                boi = boi.getUnwrappedOperation();
                LOG.trace("Unwrapped BOI {}", boi);
            }
        }
        return boi;
    }

    private void checkParameterSize(CxfEndpoint endpoint, Exchange exchange, Object[] parameters) {
        BindingOperationInfo boi = getBindingOperationInfo(exchange);
        if (boi == null) {
            throw new RuntimeCamelException("Can't find the binding operation information from camel exchange");
        }
        if (!endpoint.isWrapped()) {
            if (boi.isUnwrappedCapable()) {
                boi = boi.getUnwrappedOperation();
            }
        }
        int expectMessagePartsSize = boi.getInput().getMessageParts().size();

        if (parameters.length < expectMessagePartsSize) {
            throw new IllegalArgumentException(
                    "Get the wrong parameter size to invoke the out service, Expect size "
                                               + expectMessagePartsSize + ", Parameter size " + parameters.length
                                               + ". Please check if the message body matches the CXFEndpoint POJO Dataformat request.");
        }

        if (parameters.length > expectMessagePartsSize) {
            // need to check the holder parameters
            int holdersSize = 0;
            for (Object parameter : parameters) {
                if (parameter instanceof Holder) {
                    holdersSize++;
                }
            }
            // need to check the soap header information
            int soapHeadersSize = 0;
            BindingMessageInfo bmi = boi.getInput();
            if (bmi != null) {
                List<SoapHeaderInfo> headers = bmi.getExtensors(SoapHeaderInfo.class);
                if (headers != null) {
                    soapHeadersSize = headers.size();
                }
            }

            if (holdersSize + expectMessagePartsSize + soapHeadersSize < parameters.length) {
                throw new IllegalArgumentException(
                        "Get the wrong parameter size to invoke the out service, Expect size "
                                                   + (expectMessagePartsSize + holdersSize + soapHeadersSize)
                                                   + ", Parameter size " + parameters.length
                                                   + ". Please check if the message body matches the CXFEndpoint POJO Dataformat request.");
            }
        }
    }

    /**
     * Get the parameters for the web service operation
     */
    private Object[] getParams(CxfEndpoint endpoint, Exchange exchange)
            throws org.apache.camel.InvalidPayloadException {

        Object[] params = null;
        if (endpoint.getDataFormat() == DataFormat.POJO) {
            Object body = exchange.getIn().getBody();
            if (body == null) {
                return new Object[0];
            }
            if (body instanceof Object[]) {
                params = (Object[]) body;
            } else if (body instanceof List) {
                // Now we just check if the request is List
                params = ((List<?>) body).toArray();
            } else {
                // maybe we can iterate the body and that way create a list for the parameters
                // then end users do not need to trouble with List
                Iterator<?> it = exchange.getIn().getBody(Iterator.class);
                if (it != null && it.hasNext()) {
                    List<?> list = exchange.getContext().getTypeConverter().convertTo(List.class, it);
                    if (list != null) {
                        params = list.toArray();
                    }
                }
                if (params == null) {
                    // now we just use the body as single parameter
                    params = new Object[1];
                    params[0] = exchange.getIn().getBody();
                }
            }
            // make sure we have the right number of parameters
            checkParameterSize(endpoint, exchange, params);

        } else if (endpoint.getDataFormat() == DataFormat.PAYLOAD) {
            params = new Object[1];
            params[0] = exchange.getIn().getMandatoryBody(CxfPayload.class);
        } else if (endpoint.getDataFormat().dealias() == DataFormat.RAW) {
            params = new Object[1];
            params[0] = exchange.getIn().getMandatoryBody(InputStream.class);
        } else if (endpoint.getDataFormat().dealias() == DataFormat.CXF_MESSAGE) {
            params = new Object[1];
            params[0] = exchange.getIn().getBody();
        }

        if (LOG.isTraceEnabled()) {
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    LOG.trace("params[{}] = {}", i, params[i]);
                }
            }
        }

        return params;
    }

    /**
     * <p>
     * Get operation name from header and use it to lookup and return a {@link BindingOperationInfo}.
     * </p>
     * <p>
     * CxfProducer lookups the operation name lookup with below order, and it uses the first found one which is not
     * null:
     * </p>
     * <ul>
     * <li>Using the in message header "operationName".</li>
     * <li>Using the defaultOperationName option value from the CxfEndpoint.</li>
     * <li>Using the first operation which is find from the CxfEndpoint Operations list.</li>
     * <ul>
     */
    private BindingOperationInfo getBindingOperationInfo(Exchange ex) {
        CxfEndpoint cxfEndpoint = (CxfEndpoint) this.getEndpoint();
        BindingOperationInfo answer = null;
        String lp = ex.getIn().getHeader(CxfConstants.OPERATION_NAME, String.class);
        if (lp == null) {
            LOG.debug("CxfProducer cannot find the {} from message header, trying with defaultOperationName",
                    CxfConstants.OPERATION_NAME);
            lp = cxfEndpoint.getDefaultOperationName();
        }
        if (lp == null) {
            LOG.debug(
                    "CxfProducer cannot find the {} from message header and there is no DefaultOperationName setting, CxfProducer will pick up the first available operation.",
                    CxfConstants.OPERATION_NAME);
            Collection<BindingOperationInfo> bois = client.getEndpoint().getEndpointInfo().getBinding().getOperations();

            Iterator<BindingOperationInfo> iter = bois.iterator();
            if (iter.hasNext()) {
                answer = iter.next();
            }

        } else {
            String ns = ex.getIn().getHeader(CxfConstants.OPERATION_NAMESPACE, String.class);
            if (ns == null) {
                ns = cxfEndpoint.getDefaultOperationNamespace();
            }
            if (ns == null) {
                ns = client.getEndpoint().getService().getName().getNamespaceURI();
                LOG.trace("Operation namespace not in header. Set it to: {}", ns);
            }

            QName qname = new QName(ns, lp);

            LOG.trace("Operation qname = {}", qname);

            answer = client.getEndpoint().getEndpointInfo().getBinding().getOperation(qname);
            if (answer == null) {
                throw new IllegalArgumentException(
                        "Can't find the BindingOperationInfo with operation name " + qname
                                                   + ". Please check the message headers of operationName and operationNamespace.");
            }
        }
        return answer;
    }

    public Client getClient() {
        return client;
    }

}
