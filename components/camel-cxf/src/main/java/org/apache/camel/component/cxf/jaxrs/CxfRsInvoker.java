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
package org.apache.camel.component.cxf.jaxrs;

import java.lang.reflect.Method;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.apache.camel.AsyncCallback;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.RuntimeCamelException;
import org.apache.cxf.continuations.Continuation;
import org.apache.cxf.continuations.ContinuationProvider;
import org.apache.cxf.jaxrs.JAXRSInvoker;
import org.apache.cxf.jaxrs.impl.HttpHeadersImpl;
import org.apache.cxf.jaxrs.impl.RequestImpl;
import org.apache.cxf.jaxrs.impl.SecurityContextImpl;
import org.apache.cxf.jaxrs.impl.UriInfoImpl;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.message.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CxfRsInvoker extends JAXRSInvoker {
    private static final Logger LOG = LoggerFactory.getLogger(CxfRsInvoker.class);
    private static final String SUSPENED = "org.apache.camel.component.cxf.jaxrs.suspend";
    private final CxfRsConsumer cxfRsConsumer;
    private final CxfRsEndpoint endpoint;
    
    public CxfRsInvoker(CxfRsEndpoint endpoint, CxfRsConsumer consumer) {
        this.endpoint = endpoint;
        this.cxfRsConsumer = consumer;
    }
        
    @Override
    protected Object performInvocation(Exchange cxfExchange, final Object serviceObject, Method method,
                                       Object[] paramArray) throws Exception {
        Object response = null;
        if (endpoint.isPerformInvocation()) {
            response = super.performInvocation(cxfExchange, serviceObject, method, paramArray);
        }
        paramArray = insertExchange(method, paramArray, cxfExchange);
        OperationResourceInfo ori = cxfExchange.get(OperationResourceInfo.class);        
        if (ori.isSubResourceLocator()) {
            // don't delegate the sub resource locator call to camel processor
            return method.invoke(serviceObject, paramArray);
        }
        Continuation continuation;
        if (!endpoint.isSynchronous() && (continuation = getContinuation(cxfExchange)) != null) {
            LOG.trace("Calling the Camel async processors.");
            return asyncInvoke(cxfExchange, serviceObject, method, paramArray, continuation, response);
        } else {
            LOG.trace("Calling the Camel sync processors.");
            return syncInvoke(cxfExchange, serviceObject, method, paramArray, response);
        }
    }
    
    private Continuation getContinuation(Exchange cxfExchange) {
        ContinuationProvider provider = 
            (ContinuationProvider)cxfExchange.getInMessage().get(ContinuationProvider.class.getName());
        return provider == null ? null : provider.getContinuation();
    }
    
    private Object asyncInvoke(Exchange cxfExchange, final Object serviceObject, Method method,
                              Object[] paramArray, final Continuation continuation, Object response) throws Exception {
        synchronized (continuation) {
            if (continuation.isNew()) {
                final org.apache.camel.Exchange camelExchange = prepareExchange(cxfExchange, method, paramArray, response);
                // we want to handle the UoW
                cxfRsConsumer.createUoW(camelExchange);
                // Now we don't set up the timeout value
                LOG.trace("Suspending continuation of exchangeId: {}", camelExchange.getExchangeId());
                // The continuation could be called before the suspend is called
                continuation.suspend(endpoint.getContinuationTimeout());
                cxfExchange.put(SUSPENED, Boolean.TRUE);
                continuation.setObject(camelExchange);
                cxfRsConsumer.getAsyncProcessor().process(camelExchange, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        // make sure the continuation resume will not be called before the suspend method in other thread
                        synchronized (continuation) {
                            LOG.trace("Resuming continuation of exchangeId: {}", camelExchange.getExchangeId());
                            // resume processing after both, sync and async callbacks
                            continuation.resume();
                        }
                    }
                });
                return null;
            }
            if (!continuation.isTimeout() && continuation.isResumed()) {
                cxfExchange.put(SUSPENED, Boolean.FALSE);
                org.apache.camel.Exchange camelExchange = (org.apache.camel.Exchange)continuation.getObject();
                try {
                    return returnResponse(cxfExchange, camelExchange);
                } catch (Exception ex) {
                    cxfRsConsumer.doneUoW(camelExchange);
                    throw ex;
                }
            } else {
                if (continuation.isTimeout() || !continuation.isPending()) {
                    cxfExchange.put(SUSPENED, Boolean.FALSE);
                    org.apache.camel.Exchange camelExchange = (org.apache.camel.Exchange)continuation.getObject();
                    camelExchange.setException(new ExchangeTimedOutException(camelExchange, endpoint.getContinuationTimeout()));
                    try {
                        return returnResponse(cxfExchange, camelExchange);
                    } catch (Exception ex) {
                        cxfRsConsumer.doneUoW(camelExchange);
                        throw ex;
                    }
                }
            }
        }
        return null;
    }
    
    private Object syncInvoke(Exchange cxfExchange, final Object serviceObject, Method method,
                              Object[] paramArray,
                              Object response) throws Exception {
        final org.apache.camel.Exchange camelExchange = prepareExchange(cxfExchange, method, paramArray, response);
        // we want to handle the UoW
        cxfRsConsumer.createUoW(camelExchange);

        try {
            cxfRsConsumer.getProcessor().process(camelExchange);
        } catch (Exception exception) {
            camelExchange.setException(exception);
        }

        try {
            return returnResponse(cxfExchange, camelExchange);
        } catch (Exception ex) {
            cxfRsConsumer.doneUoW(camelExchange);
            throw  ex;
        }
    }
    
    private org.apache.camel.Exchange prepareExchange(Exchange cxfExchange, Method method,
            Object[] paramArray, Object response) {
        ExchangePattern ep = ExchangePattern.InOut;
        if (method.getReturnType() == Void.class) {
            ep = ExchangePattern.InOnly;
        } 
        final org.apache.camel.Exchange camelExchange = endpoint.createExchange(ep);
        //needs access in MessageObserver/Interceptor to close the UnitOfWork
        cxfExchange.put(org.apache.camel.Exchange.class, camelExchange);

        if (response != null) {
            camelExchange.getOut().setBody(response);
        }
        CxfRsBinding binding = endpoint.getBinding();
        binding.populateExchangeFromCxfRsRequest(cxfExchange, camelExchange, method, paramArray);
        
        // REVISIT: It can be done inside a binding but a propagateContext would need to be passed along as
        // the CXF in message property. Question: where should this property name be set up ? 
        if (endpoint.isPropagateContexts()) {
            camelExchange.setProperty(UriInfo.class.getName(), new UriInfoImpl(cxfExchange.getInMessage()));
            camelExchange.setProperty(Request.class.getName(), new RequestImpl(cxfExchange.getInMessage()));
            camelExchange.setProperty(HttpHeaders.class.getName(), new HttpHeadersImpl(cxfExchange.getInMessage()));
            camelExchange.setProperty(SecurityContext.class.getName(), new SecurityContextImpl(cxfExchange.getInMessage()));
        }
        
        return camelExchange;
    }
    
    private Object returnResponse(Exchange cxfExchange, org.apache.camel.Exchange camelExchange) throws Exception {
        if (camelExchange.getException() != null) {
            Throwable exception = camelExchange.getException();
            Object result = null;
            if (exception instanceof RuntimeCamelException) {
                // Unwrap the RuntimeCamelException
                if (exception.getCause() != null) {
                    exception = exception.getCause();
                }
            }
            if (exception instanceof WebApplicationException) {
                result = ((WebApplicationException)exception).getResponse();
                if (result != null) {
                    return result;
                } else {
                    throw (WebApplicationException)exception;
                }
            } 
            //CAMEL-7357 throw out other exception to make sure the ExceptionMapper work
        }
        return endpoint.getBinding().populateCxfRsResponseFromExchange(camelExchange, cxfExchange);
    }

}
