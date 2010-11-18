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
package org.apache.camel.component.cxf.jaxrs;

import java.lang.reflect.Method;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.camel.AsyncCallback;
import org.apache.camel.ExchangePattern;
import org.apache.camel.RuntimeCamelException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.continuations.Continuation;
import org.apache.cxf.continuations.ContinuationProvider;
import org.apache.cxf.jaxrs.JAXRSInvoker;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.version.Version;

public class CxfRsInvoker extends JAXRSInvoker {
    private static final Log LOG = LogFactory.getLog(CxfRsInvoker.class);
    private static final String SUSPENED = "org.apache.camel.component.cxf.jaxrs.suspend";
    private static final String SERVICE_OBJECT_SCOPE = "org.apache.cxf.service.scope";
    private static final String REQUEST_SCOPE = "request";
    private CxfRsConsumer cxfRsConsumer;
    private CxfRsEndpoint endpoint;
    
    public CxfRsInvoker(CxfRsEndpoint endpoint, CxfRsConsumer consumer) {
        this.endpoint = endpoint;
        this.cxfRsConsumer = consumer;
    }
    
    // To work around the invoke reenter issue
    public Object invoke(Exchange exchange, Object request) {
        // These code 
        Response response = exchange.get(Response.class);
        if (response != null) {
            return new MessageContentsList(response);
        }
        ResourceProvider provider = getResourceProvider(exchange);
        Object serviceObject = getServiceObject(exchange);
        try {
            return invoke(exchange, request, serviceObject);
        } finally {
            if (Boolean.TRUE.equals(exchange.get(SUSPENED))) {
                // should check the 
                exchange.put(JAXRSUtils.ROOT_INSTANCE, serviceObject);
                exchange.put(JAXRSUtils.ROOT_PROVIDER, provider);
            } else {
                // clean up the resource
                if (exchange.isOneWay()) {
                    ProviderFactory.getInstance(exchange.getInMessage()).clearThreadLocalProxies();
                }
                if (!isServiceObjectRequestScope(exchange.getInMessage())) {
                    provider.releaseInstance(exchange.getInMessage(), serviceObject);
                }
                exchange.remove(JAXRSUtils.ROOT_INSTANCE);
                exchange.remove(JAXRSUtils.ROOT_PROVIDER);
            }
        }
    }
    
    public Object getServiceObject(Exchange exchange) {
        if (exchange.get(JAXRSUtils.ROOT_INSTANCE) != null) {
            return exchange.get(JAXRSUtils.ROOT_INSTANCE);
        } else {
            OperationResourceInfo ori = exchange.get(OperationResourceInfo.class);
            ClassResourceInfo cri = ori.getClassResourceInfo();
            return cri.getResourceProvider().getInstance(exchange.getInMessage());
        }
    }
        
    private ResourceProvider getResourceProvider(Exchange exchange) {
        if (exchange.get(JAXRSUtils.ROOT_PROVIDER) != null) {
            return (ResourceProvider)exchange.get(JAXRSUtils.ROOT_PROVIDER);
        } else {
            OperationResourceInfo ori = exchange.get(OperationResourceInfo.class);
            ClassResourceInfo cri = ori.getClassResourceInfo();
            return cri.getResourceProvider();
        }
    }
    
    private boolean isServiceObjectRequestScope(Message inMessage) {
        Object scope = inMessage.getContextualProperty(SERVICE_OBJECT_SCOPE);
        return REQUEST_SCOPE.equals(scope);
    }
    
    protected Object performInvocation(Exchange cxfExchange, final Object serviceObject, Method method,
                                       Object[] paramArray) throws Exception {
        paramArray = insertExchange(method, paramArray, cxfExchange);
        OperationResourceInfo ori = cxfExchange.get(OperationResourceInfo.class);        
        if (ori.isSubResourceLocator()) {
            // don't delegate the sub resource locator call to camel processor
            return method.invoke(serviceObject, paramArray);
        }
        Continuation continuation = getContinuation(cxfExchange);
        // Only calling the continuation API for CXF 2.3.x 
        if (continuation != null && !endpoint.isSynchronous() && Version.getCurrentVersion().startsWith("2.3")) {
            return asyncInvoke(cxfExchange, serviceObject, method, paramArray, continuation);
        } else {
            return syncInvoke(cxfExchange, serviceObject, method, paramArray);
        }
    }
    
    private Continuation getContinuation(Exchange cxfExchange) {
        ContinuationProvider provider = 
            (ContinuationProvider)cxfExchange.getInMessage().get(ContinuationProvider.class.getName());
        return provider == null ? null : provider.getContinuation();
    }
    
    private Object asyncInvoke(Exchange cxfExchange, final Object serviceObject, Method method,
                              Object[] paramArray, final Continuation continuation) throws Exception {
        synchronized (continuation) {
            if (continuation.isNew()) {
                ExchangePattern ep = ExchangePattern.InOut;
                if (method.getReturnType() == Void.class) {
                    ep = ExchangePattern.InOnly;
                } 
                final org.apache.camel.Exchange camelExchange = endpoint.createExchange(ep);
                CxfRsBinding binding = endpoint.getBinding();
                binding.populateExchangeFromCxfRsRequest(cxfExchange, camelExchange, method, paramArray);
                // Now we don't set up the timeout value
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Suspending continuation of exchangeId: " + camelExchange.getExchangeId());
                }
                // TODO Support to set the timeout in case the Camel can't send the response back on time.
                // The continuation could be called before the suspend is called
                continuation.suspend(0);
                cxfExchange.put(SUSPENED, Boolean.TRUE);
                cxfRsConsumer.getAsyncProcessor().process(camelExchange, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        // make sure the continuation resume will not be called before the suspend method in other thread
                        synchronized (continuation) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Resuming continuation of exchangeId: " + camelExchange.getExchangeId());
                            }
                            // resume processing after both, sync and async callbacks
                            continuation.setObject(camelExchange);
                            continuation.resume();
                        }
                    }
                });
                return null;
            }
            if (continuation.isResumed()) {
                cxfExchange.put(SUSPENED, Boolean.FALSE);
                org.apache.camel.Exchange camelExchange = (org.apache.camel.Exchange)continuation
                    .getObject();
                return returnResponse(cxfExchange, camelExchange);
            }
        }
        return null;
    }
    
    private Object syncInvoke(Exchange cxfExchange, final Object serviceObject, Method method,
                              Object[] paramArray) throws Exception {
        ExchangePattern ep = ExchangePattern.InOut;
        
        if (method.getReturnType() == Void.class) {
            ep = ExchangePattern.InOnly;
        } 
        org.apache.camel.Exchange camelExchange = endpoint.createExchange(ep);
        CxfRsBinding binding = endpoint.getBinding();
        binding.populateExchangeFromCxfRsRequest(cxfExchange, camelExchange, method, paramArray);
        
        try {
            cxfRsConsumer.getProcessor().process(camelExchange);
        } catch (Exception exception) {
            camelExchange.setException(exception);
        }
        return returnResponse(cxfExchange, camelExchange);
        
    }
    
    private Object returnResponse(Exchange cxfExchange, org.apache.camel.Exchange camelExchange) throws Exception {
        if (camelExchange.getException() != null) {
            Throwable exception = camelExchange.getException();
            Object result = null;
            if (exception instanceof RuntimeCamelException) {
                exception = exception.getCause();
            }
            if (exception instanceof WebApplicationException) {
                result = ((WebApplicationException)exception).getResponse();
            }
            return result;
        }
        return endpoint.getBinding().populateCxfRsResponseFromExchange(camelExchange, cxfExchange);
    }

}
