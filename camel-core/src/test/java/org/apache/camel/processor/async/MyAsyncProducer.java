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
package org.apache.camel.processor.async;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class MyAsyncProducer implements AsyncProcessor, Producer {

    private static final Log LOG = LogFactory.getLog(MyAsyncProducer.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MyAsyncEndpoint endpoint;

    public MyAsyncProducer(MyAsyncEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        executor.submit(new Callable<Object>() {
            public Object call() throws Exception {
                LOG.info("Simulating a task which takes 2 sec to reply");

                Thread.sleep(2000);
                String reply = endpoint.getReply();
                exchange.getOut().setBody(reply);

                LOG.info("Callback done(false)");
                callback.done(false);
                return null;
            }
        });

        // indicate from this point forward its being routed asynchronously
        LOG.info("Task submitted, now tell Camel routing engine to that this Exchange is being continued asynchronously");
        return false;
    }

    public MyAsyncEndpoint getEndpoint() {
        return endpoint;
    }

    public Exchange createExchange() {
        return new DefaultExchange(endpoint);
    }

    public Exchange createExchange(ExchangePattern pattern) {
        return new DefaultExchange(endpoint, pattern);
    }

    public Exchange createExchange(Exchange exchange) {
        return new DefaultExchange(exchange);
    }

    public void start() throws Exception {
    }

    public void stop() throws Exception {
    }

    public boolean isSingleton() {
        return true;
    }
}
