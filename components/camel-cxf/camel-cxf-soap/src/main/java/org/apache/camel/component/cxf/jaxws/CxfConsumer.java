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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import jakarta.xml.ws.WebFault;

import org.w3c.dom.Element;

import org.apache.camel.AsyncCallback;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.component.cxf.common.CxfBinding;
import org.apache.camel.component.cxf.common.DataFormat;
import org.apache.camel.component.cxf.common.UnitOfWorkCloserInterceptor;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.continuations.Continuation;
import org.apache.cxf.continuations.ContinuationProvider;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.FaultMode;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.service.invoker.Invoker;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.ContextUtils;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Consumer of exchanges for a service in CXF. CxfConsumer acts a CXF service to receive requests, convert them, and
 * forward them to Camel route for processing. It is also responsible for converting and sending back responses to CXF
 * client.
 */
public class CxfConsumer extends DefaultConsumer implements Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(CxfConsumer.class);

    private Server server;
    private CxfEndpoint cxfEndpoint;

    public CxfConsumer(final CxfEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        cxfEndpoint = endpoint;
    }

    protected Server createServer() throws Exception {
        ServerFactoryBean svrBean = cxfEndpoint.createServerFactoryBean();
        svrBean.setInvoker(new CxfConsumerInvoker(cxfEndpoint));
        final Server ret = svrBean.create();
        // Apply the server configurer if it is possible
        if (cxfEndpoint.getCxfConfigurer() != null) {
            cxfEndpoint.getCxfConfigurer().configureServer(ret);
        }
        ret.getEndpoint().getEndpointInfo().setProperty("serviceClass", cxfEndpoint.getServiceClass());
        if (ObjectHelper.isNotEmpty(cxfEndpoint.getPublishedEndpointUrl())) {
            ret.getEndpoint().getEndpointInfo().setProperty("publishedEndpointUrl", cxfEndpoint.getPublishedEndpointUrl());
        }

        final MessageObserver originalOutFaultObserver = ret.getEndpoint().getOutFaultObserver();
        ret.getEndpoint().setOutFaultObserver(message -> {
            originalOutFaultObserver.onMessage(message);
        });

        // setup the UnitOfWorkCloserInterceptor for OneWayMessageProcessor
        ret.getEndpoint().getInInterceptors().add(new UnitOfWorkCloserInterceptor(Phase.POST_INVOKE, true));
        // close the UnitOfWork normally
        ret.getEndpoint().getOutInterceptors().add(new UnitOfWorkCloserInterceptor());
        // close the UnitOfWork in case of Fault
        ret.getEndpoint().getOutFaultInterceptors().add(new UnitOfWorkCloserInterceptor());
        return ret;
    }

    public Server getServer() {
        return server;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (server == null) {
            server = createServer();
        }
        server.start();
    }

    @Override
    protected void doStop() throws Exception {
        if (server != null) {
            server.stop();
            server.destroy();
            server = null;
        }
        super.doStop();
    }

    private EndpointReferenceType getReplyTo(Object o) {
        try {
            return (EndpointReferenceType) o.getClass().getMethod("getReplyTo").invoke(o);
        } catch (Exception t) {
            throw new Fault(t);
        }
    }

    protected boolean isAsyncInvocationSupported(Exchange cxfExchange) {
        Message cxfMessage = cxfExchange.getInMessage();
        Object addressingProperties = cxfMessage.get(CxfConstants.WSA_HEADERS_INBOUND);
        if (addressingProperties != null
                && !ContextUtils.isGenericAddress(getReplyTo(addressingProperties))) {
            //it's decoupled endpoint, so already switch thread and
            //use executors, which means underlying transport won't
            //be block, so we shouldn't rely on continuation in
            //this case, as the SuspendedInvocationException can't be
            //caught by underlying transport. So we should use the SyncInvocation this time
            return false;
        }
        // we assume it should support AsyncInvocation out of box
        return true;
    }

    private class CxfConsumerInvoker implements Invoker {

        private final CxfEndpoint endpoint;

        CxfConsumerInvoker(CxfEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        // we receive a CXF request when this method is called
        @Override
        public Object invoke(Exchange cxfExchange, Object o) {
            LOG.trace("Received CXF Request: {}", cxfExchange);
            Continuation continuation;
            if (!endpoint.isSynchronous() && isAsyncInvocationSupported(cxfExchange)
                    && (continuation = getContinuation(cxfExchange)) != null) {
                LOG.trace("Calling the Camel async processors.");
                return asyncInvoke(cxfExchange, continuation);
            } else {
                LOG.trace("Calling the Camel sync processors.");
                return syncInvoke(cxfExchange);
            }
        }

        // NOTE this code cannot work with CXF 2.2.x and JMSContinuation
        // as it doesn't break out the interceptor chain when we call it
        private Object asyncInvoke(Exchange cxfExchange, final Continuation continuation) {
            LOG.trace("asyncInvoke continuation: {}", continuation);
            synchronized (continuation) {
                if (continuation.isNew()) {
                    final org.apache.camel.Exchange camelExchange = prepareCamelExchange(cxfExchange);

                    // Now we don't set up the timeout value
                    LOG.trace("Suspending continuation of exchangeId: {}", camelExchange.getExchangeId());

                    // The continuation could be called before the suspend is called
                    continuation.suspend(cxfEndpoint.getContinuationTimeout());

                    continuation.setObject(camelExchange);

                    // use the asynchronous API to process the exchange
                    getAsyncProcessor().process(camelExchange, new AsyncCallback() {
                        public void done(boolean doneSync) {
                            // make sure the continuation resume will not be called before the suspend method in other thread
                            synchronized (continuation) {
                                LOG.trace("Resuming continuation of exchangeId: {}", camelExchange.getExchangeId());
                                // resume processing after both, sync and async callbacks
                                continuation.resume();
                            }
                        }
                    });

                } else if (!continuation.isTimeout() && continuation.isResumed()) {
                    org.apache.camel.Exchange camelExchange = (org.apache.camel.Exchange) continuation.getObject();
                    try {
                        setResponseBack(cxfExchange, camelExchange);
                    } catch (Exception ex) {
                        CxfConsumer.this.doneUoW(camelExchange);
                        throw ex;
                    }

                } else if (continuation.isTimeout() || !continuation.isResumed() && !continuation.isPending()) {
                    org.apache.camel.Exchange camelExchange = (org.apache.camel.Exchange) continuation.getObject();
                    try {
                        if (!continuation.isPending()) {
                            camelExchange.setException(
                                    new ExchangeTimedOutException(camelExchange, cxfEndpoint.getContinuationTimeout()));
                        }
                        setResponseBack(cxfExchange, camelExchange);
                    } catch (Exception ex) {
                        CxfConsumer.this.doneUoW(camelExchange);
                        throw ex;
                    }
                }
            }
            return null;
        }

        private Continuation getContinuation(Exchange cxfExchange) {
            ContinuationProvider provider
                    = (ContinuationProvider) cxfExchange.getInMessage().get(ContinuationProvider.class.getName());
            Continuation continuation = provider == null ? null : provider.getContinuation();
            // Make sure we don't return the JMSContinuation, as it doesn't support the Continuation we wants
            // Don't want to introduce the dependency of cxf-rt-transprot-jms here
            if (continuation != null
                    && continuation.getClass().getName().equals("org.apache.cxf.transport.jms.continuations.JMSContinuation")) {
                return null;
            } else {
                return continuation;
            }
        }

        private Object syncInvoke(Exchange cxfExchange) {
            org.apache.camel.Exchange camelExchange = prepareCamelExchange(cxfExchange);
            try {
                try {
                    LOG.trace("Processing +++ START +++");
                    // send Camel exchange to the target processor
                    getProcessor().process(camelExchange);
                } catch (Exception e) {
                    throw new Fault(e);
                }

                LOG.trace("Processing +++ END +++");
                setResponseBack(cxfExchange, camelExchange);
            } catch (Exception ex) {
                doneUoW(camelExchange);
                throw ex;
            }
            // response should have been set in outMessage's content
            return null;
        }

        private org.apache.camel.Exchange prepareCamelExchange(Exchange cxfExchange) {
            // get CXF binding
            CxfEndpoint endpoint = (CxfEndpoint) getEndpoint();
            CxfBinding binding = endpoint.getCxfBinding();

            // create a Camel exchange, the default MEP is InOut
            org.apache.camel.Exchange camelExchange = endpoint.createExchange();
            //needs access in MessageObserver/Interceptor to close the UnitOfWork
            cxfExchange.put(org.apache.camel.Exchange.class, camelExchange);

            DataFormat dataFormat = endpoint.getDataFormat();

            BindingOperationInfo boi = cxfExchange.getBindingOperationInfo();
            // make sure the "boi" is remained as wrapped in PAYLOAD mode
            if (boi != null && dataFormat == DataFormat.PAYLOAD && boi.isUnwrapped()) {
                boi = boi.getWrappedOperation();
                cxfExchange.put(BindingOperationInfo.class, boi);
            }

            if (boi != null) {
                camelExchange.setProperty(BindingOperationInfo.class.getName(), boi);
                LOG.trace("Set exchange property: BindingOperationInfo: {}", boi);
                // set the message exchange patter with the boi
                if (boi.getOperationInfo().isOneWay()) {
                    camelExchange.setPattern(ExchangePattern.InOnly);
                }
            } else {
                if (cxfEndpoint.getExchangePattern().equals(ExchangePattern.InOnly)) {
                    camelExchange.setPattern(ExchangePattern.InOnly);
                }
            }

            // set data format mode in Camel exchange
            camelExchange.setProperty(CxfConstants.DATA_FORMAT_PROPERTY, dataFormat);
            LOG.trace("Set Exchange property: {}={}", DataFormat.class.getName(), dataFormat);

            camelExchange.setProperty(Message.MTOM_ENABLED, String.valueOf(endpoint.isMtomEnabled()));

            if (endpoint.isMergeProtocolHeaders()) {
                camelExchange.setProperty(CxfConstants.CAMEL_CXF_PROTOCOL_HEADERS_MERGED, Boolean.TRUE);
            }
            // bind the CXF request into a Camel exchange
            binding.populateExchangeFromCxfRequest(cxfExchange, camelExchange);
            // extract the jakarta.xml.ws header
            Map<String, Object> context = new HashMap<>();
            binding.extractJaxWsContext(cxfExchange, context);
            // put the context into camelExchange
            camelExchange.setProperty(CxfConstants.JAXWS_CONTEXT, context);

            // we want to handle the UoW
            try {
                CxfConsumer.this.createUoW(camelExchange);
            } catch (Exception e) {
                LOG.error("Error processing request", e);
                throw new Fault(e);
            }
            return camelExchange;
        }

        @SuppressWarnings("unchecked")
        private void setResponseBack(Exchange cxfExchange, org.apache.camel.Exchange camelExchange) {
            CxfEndpoint endpoint = (CxfEndpoint) getEndpoint();
            CxfBinding binding = endpoint.getCxfBinding();

            ((DefaultCxfBinding) binding).populateCxfHeaderFromCamelExchangeBeforeCheckError(camelExchange, cxfExchange);
            checkFailure(camelExchange, cxfExchange);

            binding.populateCxfResponseFromExchange(camelExchange, cxfExchange);

            // check failure again as fault could be discovered by converter
            checkFailure(camelExchange, cxfExchange);

            // copy the headers jakarta.xml.ws header back
            binding.copyJaxWsContext(cxfExchange, (Map<String, Object>) camelExchange.getProperty(CxfConstants.JAXWS_CONTEXT));
        }

        private void checkFailure(org.apache.camel.Exchange camelExchange, Exchange cxfExchange) throws Fault {
            Throwable t = camelExchange.getException();
            if (t == null) {
                // SOAP faults can be stored as exceptions as message body (to be backwards compatible)
                Object body = camelExchange.getMessage().getBody();
                if (body instanceof Throwable) {
                    t = (Throwable) body;
                }
            }

            if (t != null) {
                cxfExchange.getInMessage().put(FaultMode.class, FaultMode.UNCHECKED_APPLICATION_FAULT);
                if (t instanceof Fault) {
                    cxfExchange.getInMessage().put(FaultMode.class, FaultMode.CHECKED_APPLICATION_FAULT);
                    throw (Fault) t;
                } else {
                    // This is not a CXF Fault. Build the CXF Fault manually.
                    Fault fault = new Fault(t);
                    if (fault.getMessage() == null) {
                        // The Fault has no Message. This is the case if it has
                        // no message, for example was a NullPointerException.
                        fault.setMessage(t.getClass().getSimpleName());
                    }
                    WebFault faultAnnotation = t.getClass().getAnnotation(WebFault.class);
                    Object faultInfo = null;
                    try {
                        Method method = t.getClass().getMethod("getFaultInfo");
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

    }
}
