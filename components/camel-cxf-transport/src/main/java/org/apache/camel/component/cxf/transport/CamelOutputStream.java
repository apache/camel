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
package org.apache.camel.component.cxf.transport;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.component.cxf.common.header.CxfHeaderHelper;
import org.apache.camel.component.cxf.common.message.CxfMessageHelper;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.Bus;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.workqueue.AutomaticWorkQueue;
import org.apache.cxf.workqueue.WorkQueueManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CamelOutputStream extends CachedOutputStream {
    private static final Logger LOG = LoggerFactory.getLogger(CamelOutputStream.class);
    
    /**
     * 
     */
    private final Message outMessage;
    private boolean isOneWay;
    private String targetCamelEndpointUri;
    private Producer producer;
    private HeaderFilterStrategy headerFilterStrategy;
    private MessageObserver observer;
    private boolean hasLoggedAsyncWarning;

    CamelOutputStream(String targetCamelEndpointUri, Producer producer,
                      HeaderFilterStrategy headerFilterStrategy, MessageObserver observer,
                      Message m) {
        this.targetCamelEndpointUri = targetCamelEndpointUri;
        this.producer = producer;
        this.headerFilterStrategy = headerFilterStrategy;
        this.observer = observer;
        outMessage = m;
    }

    @Override
    protected void doFlush() throws IOException {
        // do nothing here
    }

    @Override
    protected void doClose() throws IOException {
        isOneWay = outMessage.getExchange().isOneWay();
        
        commitOutputMessage();
    }

    @Override
    protected void onWrite() throws IOException {
        // do nothing here
    }


    private void commitOutputMessage() throws IOException {
        ExchangePattern pattern;
        if (isOneWay) {
            pattern = ExchangePattern.InOnly;
        } else {
            pattern = ExchangePattern.InOut;
        }
        LOG.debug("send the message to endpoint {}", this.targetCamelEndpointUri);
        final org.apache.camel.Exchange exchange = this.producer.getEndpoint().createExchange(pattern);

        exchange.setProperty(Exchange.TO_ENDPOINT, this.targetCamelEndpointUri);
        CachedOutputStream outputStream = (CachedOutputStream) outMessage.getContent(OutputStream.class);
        // Send out the request message here, copy the protocolHeader back
        CxfHeaderHelper.propagateCxfToCamel(this.headerFilterStrategy, outMessage, exchange.getIn(), exchange);

        // TODO support different encoding
        exchange.getIn().setBody(outputStream.getInputStream());
        LOG.debug("template sending request: {}", exchange.getIn());
        
        if (outMessage.getExchange().isSynchronous()) {
            syncInvoke(exchange);
        } else {
            // submit the request to the work queue
            asyncInvokeFromWorkQueue(exchange);
        }

    }
    
    protected void syncInvoke(org.apache.camel.Exchange exchange) throws IOException {
        try {
            this.producer.process(exchange);
        } catch (Exception ex) {
            exchange.setException(ex);
        }
        // Throw the exception that the template get
        Exception exception = exchange.getException();
        if (exception != null) {
            throw new IOException("Cannot send the request message.", exchange.getException());
        }
        exchange.setProperty(CamelTransportConstants.CXF_EXCHANGE, outMessage.getExchange());
        if (!isOneWay) {
            handleResponseInternal(exchange);
        }
        
    }
     
    protected void asyncInvokeFromWorkQueue(final org.apache.camel.Exchange exchange) throws IOException {
        Runnable runnable = new Runnable() {
            public void run() {
                try {
                    syncInvoke(exchange);
                } catch (Throwable e) {
                    ((PhaseInterceptorChain)outMessage.getInterceptorChain()).abort();
                    outMessage.setContent(Exception.class, e);
                    ((PhaseInterceptorChain)outMessage.getInterceptorChain()).unwind(outMessage);
                    MessageObserver mo = outMessage.getInterceptorChain().getFaultObserver();
                    if (mo == null) {
                        mo = outMessage.getExchange().get(MessageObserver.class);
                    }
                    mo.onMessage(outMessage);
                }
            }
        };
        
        try {
            Executor ex = outMessage.getExchange().get(Executor.class);
            if (ex != null) {
                outMessage.getExchange().put(Executor.class.getName() 
                                             + ".USING_SPECIFIED", Boolean.TRUE);
                ex.execute(runnable);
            } else {
                WorkQueueManager mgr = outMessage.getExchange().get(Bus.class)
                    .getExtension(WorkQueueManager.class);
                AutomaticWorkQueue qu = mgr.getNamedWorkQueue("camel-cxf-conduit");
                if (qu == null) {
                    qu = mgr.getAutomaticWorkQueue();
                }
                // need to set the time out somewhere
                qu.execute(runnable);
            } 
        } catch (RejectedExecutionException rex) {
            if (!hasLoggedAsyncWarning) {
                LOG.warn("Executor rejected background task to retrieve the response.  Suggest increasing the workqueue settings.");
                hasLoggedAsyncWarning = true;
            }
            LOG.info("Executor rejected background task to retrieve the response, running on current thread.");
            syncInvoke(exchange);
        }
    }

    private void handleResponseInternal(org.apache.camel.Exchange exchange) {
        org.apache.cxf.message.Message inMessage = null;
        inMessage = CxfMessageHelper.getCxfInMessage(this.headerFilterStrategy, exchange, true);
        this.observer.onMessage(inMessage);
    }
    
    
}
