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
import org.apache.cxf.endpoint.ClientCallback;
import org.apache.cxf.endpoint.ConduitSelector;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CxfClientCallback extends ClientCallback {
    private static final Logger LOG = LoggerFactory.getLogger(CxfClientCallback.class);

    private final AsyncCallback camelAsyncCallback;
    private final Exchange camelExchange;
    private final org.apache.cxf.message.Exchange cxfExchange;
    private final BindingOperationInfo boi;
    private final CxfBinding binding;
    
    
    public CxfClientCallback(AsyncCallback callback, 
                             Exchange camelExchange,
                             org.apache.cxf.message.Exchange cxfExchange,
                             BindingOperationInfo boi,
                             CxfBinding binding) {
        this.camelAsyncCallback = callback;
        this.camelExchange = camelExchange;
        this.cxfExchange = cxfExchange;
        this.boi = boi;
        this.binding = binding;       
    }
    
    public void handleResponse(Map<String, Object> ctx, Object[] res) {
        try {
            super.handleResponse(ctx, res);            
        } finally {
            // bind the CXF response to Camel exchange and
            // call camel callback
            // for one way messages callback is already called in 
            // process method of org.apache.camel.component.cxf.CxfProducer
            if (!boi.getOperationInfo().isOneWay()) {
                binding.populateExchangeFromCxfResponse(camelExchange, cxfExchange, ctx);
                camelAsyncCallback.done(false);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} calling handleResponse", Thread.currentThread().getName());
            }
        }
    }
    
    public void handleException(Map<String, Object> ctx, Throwable ex) {
        try {
            super.handleException(ctx, ex);
            // need to call the conduitSelector complete method to enable the fail over feature
            ConduitSelector conduitSelector = cxfExchange.get(ConduitSelector.class);
            if (conduitSelector != null) {
                conduitSelector.complete(cxfExchange);
                ex = cxfExchange.getOutMessage().getContent(Exception.class);
                if (ex == null && cxfExchange.getInMessage() != null) {
                    ex = cxfExchange.getInMessage().getContent(Exception.class);
                }
                if (ex != null) {
                    camelExchange.setException(ex);
                }
            } else {
                camelExchange.setException(ex);
            }
        } finally {
            // copy the context information and 
            // call camel callback
            // for one way messages callback is already called in 
            // process method of org.apache.camel.component.cxf.CxfProducer
            if (!boi.getOperationInfo().isOneWay()) {
                binding.populateExchangeFromCxfResponse(camelExchange, cxfExchange, ctx);
                camelAsyncCallback.done(false);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} calling handleException", Thread.currentThread().getName());
            }
        }
    }        

}
