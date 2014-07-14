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

import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The xmlrpc producer.
 */
public class XmlRpcProducer extends DefaultProducer implements AsyncProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(XmlRpcProducer.class);
    private XmlRpcEndpoint endpoint;
    private XmlRpcClient client;

    public XmlRpcProducer(XmlRpcEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        LOG.trace("Process exchange: {} in the sync way.", exchange);
        Message in = exchange.getIn();
        String methodName = in.getHeader(XmlRpcConstants.METHOD_NAME, String.class);
        if (ObjectHelper.isEmpty(methodName)) {
            methodName = endpoint.getDefaultMethodName();
        }
        if (ObjectHelper.isEmpty(methodName)) {
            throw new IllegalArgumentException("CamelXmlRpcMethodName header is empty, please set the message header or defaultMethodName option on the endpoint.");
        }
        try {
            //TODO need to use the binding to handle the requests
            Object result = client.execute(methodName, in.getBody(List.class));
            //TODO what if the request is one way operation
            // copy the in message header to the out message
            exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
            exchange.getOut().setBody(result);
        } catch (Exception ex) {
            LOG.warn("Got an exception {0} when invoke the XMLRPC service", ex);
            exchange.setException(ex);
        }
    }
    
    public boolean process(Exchange exchange, AsyncCallback callback) {
        LOG.trace("Process exchange: {} in the async way.", exchange);
        Message in = exchange.getIn();
        String methodName = in.getHeader(XmlRpcConstants.METHOD_NAME, String.class);
        if (ObjectHelper.isEmpty(methodName)) {
            methodName = endpoint.getDefaultMethodName();
        }
        if (ObjectHelper.isEmpty(methodName)) {
            throw new IllegalArgumentException("CamelXmlRpcMethodName header is empty, please set the message header or defaultMethodName option on the endpoint.");
        }
        XmlRpcAsyncCallback xmlRpcAsyncCallback = new XmlRpcAsyncCallback(exchange, callback);
        //TODO need to use the binding to handle the requests
        try {
            client.executeAsync(methodName, in.getBody(List.class), xmlRpcAsyncCallback);
            return false;
        } catch (Exception ex) {
            exchange.setException(ex);
            callback.done(true);
            return true;
        }
    }
    
    @Override
    protected void doStart() throws Exception {
        if (client == null) {
            client = endpoint.createClient();
        }
    }
    
    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (client != null) {
            // Just release the client
            client = null;
        }
    }

}
