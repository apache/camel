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

import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.endpoint.ClientCallback;
import org.apache.cxf.service.model.BindingOperationInfo;

public class CxfClientCallback extends ClientCallback {
    private static final Log LOG = LogFactory.getLog(CxfClientCallback.class);

    private final AsyncCallback camelAsyncCallback;
    private final Exchange camelExchange;
    private final org.apache.cxf.message.Exchange cxfExchange;
    private final BindingOperationInfo boi;
    private final CxfEndpoint endpoint;
    
    public CxfClientCallback(AsyncCallback callback, 
                             Exchange camelExchange,
                             org.apache.cxf.message.Exchange cxfExchange,
                             BindingOperationInfo boi,
                             CxfEndpoint endpoint) {
        this.camelAsyncCallback = callback;
        this.camelExchange = camelExchange;
        this.cxfExchange = cxfExchange;
        this.boi = boi;
        this.endpoint = endpoint;       
    }
    
    public void handleResponse(Map<String, Object> ctx, Object[] res) {
        try {
            super.handleResponse(ctx, res);            
        } finally {
            // bind the CXF response to Camel exchange
            if (!boi.getOperationInfo().isOneWay()) {
                // copy the InMessage header to OutMessage header
                camelExchange.getOut().getHeaders().putAll(camelExchange.getIn().getHeaders());
                endpoint.getCxfBinding().populateExchangeFromCxfResponse(camelExchange, cxfExchange,
                        ctx);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug(Thread.currentThread().getName() + " calling handleResponse");
            }
            camelAsyncCallback.done(false);
        }
    }
    
    public void handleException(Map<String, Object> ctx, Throwable ex) {
        try {
            super.handleException(ctx, ex);
            camelExchange.setException(ex);
        } finally {
            // copy the context information
            if (!boi.getOperationInfo().isOneWay()) {
                // copy the InMessage header to OutMessage header
                camelExchange.getOut().getHeaders().putAll(camelExchange.getIn().getHeaders());
                endpoint.getCxfBinding().populateExchangeFromCxfResponse(camelExchange, cxfExchange,
                                                                         ctx);
                // set the camelExchange outbody with the exception
                camelExchange.getOut().setFault(true);
                camelExchange.getOut().setBody(ex);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug(Thread.currentThread().getName() + " calling handleException");
            }
            camelAsyncCallback.done(false);
        }
    }        

}
