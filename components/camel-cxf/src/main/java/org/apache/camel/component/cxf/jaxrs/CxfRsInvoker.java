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

import org.apache.camel.AsyncCallback;
import org.apache.camel.ExchangePattern;
import org.apache.camel.RuntimeCamelException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.continuations.Continuation;
import org.apache.cxf.continuations.ContinuationProvider;
import org.apache.cxf.jaxrs.JAXRSInvoker;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.message.Exchange;

public class CxfRsInvoker extends JAXRSInvoker {
    private static final Log LOG = LogFactory.getLog(CxfRsInvoker.class);
    private CxfRsConsumer cxfRsConsumer;
    private CxfRsEndpoint endpoint;
    
    public CxfRsInvoker(CxfRsEndpoint endpoint, CxfRsConsumer consumer) {
        this.endpoint = endpoint;
        this.cxfRsConsumer = consumer;
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
        if (continuation != null && !endpoint.isSynchronous()) {
            return asyncInvoke(cxfExchange, serviceObject, method, paramArray, continuation);
        } else {
            return syncInvoke(cxfExchange, serviceObject, method, paramArray);
        }
    }
    
    private Continuation getContinuation(Exchange cxfExchange) {
        ContinuationProvider provider = 
            (ContinuationProvider)cxfExchange.getInMessage().get(ContinuationProvider.class.getName());
        return provider.getContinuation();
    }
    
    private Object asyncInvoke(Exchange cxfExchange, final Object serviceObject, Method method,
                              Object[] paramArray, final Continuation continuation) throws Exception {
        if (continuation.isNew()) {
            ExchangePattern ep = ExchangePattern.InOut;
            if (method.getReturnType() == Void.class) {
                ep = ExchangePattern.InOnly;
            } 
            final org.apache.camel.Exchange camelExchange = endpoint.createExchange(ep);
            CxfRsBinding binding = endpoint.getBinding();
            binding.populateExchangeFromCxfRsRequest(cxfExchange, camelExchange, method, paramArray);
            boolean sync = cxfRsConsumer.getAsyncProcessor().process(camelExchange, new AsyncCallback() {
                public void done(boolean doneSync) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Resuming continuation of exchangeId: " + camelExchange.getExchangeId());
                    }
                    // resume processing after both, sync and async callbacks
                    continuation.setObject(camelExchange);
                    continuation.resume();
                }
            });
            // just need to avoid the continuation.resume is called
            // before the continuation.suspend is called
            if (continuation.getObject() != camelExchange && !sync) {
                // Now we don't set up the timeout value
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Suspending continuation of exchangeId: " + camelExchange.getExchangeId());
                }
                // The continuation could be called before the suspend
                // is called
                continuation.suspend(0);
                
            } else {
                // just set the response back, as the invoking thread is
                // not changed
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processed the Exchange : " + camelExchange.getExchangeId());
                }
                return returnResponse(cxfExchange, camelExchange);
            }

        }
        if (continuation.isResumed()) {
            org.apache.camel.Exchange camelExchange = (org.apache.camel.Exchange)continuation
                .getObject();
            return returnResponse(cxfExchange, camelExchange);

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
