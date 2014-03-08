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

package org.apache.camel.component.xmlrpc;

import org.apache.camel.Exchange;
import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.client.AsyncCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
public class XmlRpcAsyncCallback implements AsyncCallback {
    private static final Logger LOG = LoggerFactory.getLogger(XmlRpcAsyncCallback.class);

    private final org.apache.camel.AsyncCallback camelAsyncCallback;
    private final Exchange camelExchange;
   
    
    public XmlRpcAsyncCallback(Exchange exchange, org.apache.camel.AsyncCallback callback) {
        this.camelAsyncCallback = callback;
        this.camelExchange = exchange;
    }

    @Override
    public void handleResult(XmlRpcRequest pRequest, Object pResult) {
        LOG.trace("Get the response {}", pResult);
        camelExchange.getOut().setHeaders(camelExchange.getIn().getHeaders());
        camelExchange.getOut().setBody(pResult);
        camelAsyncCallback.done(false);
    }

    @Override
    public void handleError(XmlRpcRequest pRequest, Throwable pError) {
        LOG.trace("Get the Error {}", pError);
        camelExchange.setException(pError);
        camelAsyncCallback.done(false);
    }

}
