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
package org.apache.camel.support;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;

/**
 * To process the delegated producer in synchronous mode.
 * <p/>
 * This is used to enforce asynchronous producers to run in synchronous mode when it has been configured to do so.
 * <p/>
 * This delegate allows the component developers easily to support their existing asynchronous producer to behave
 * synchronously by wrapping their producer in this synchronous delegate.
 */
public class SynchronousDelegateProducer extends ServiceSupport implements Producer {

    private final Producer producer;

    public SynchronousDelegateProducer(Producer producer) {
        this.producer = producer;
    }

    @Override
    public Endpoint getEndpoint() {
        return producer.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        producer.process(exchange);
    }

    @Override
    protected void doBuild() throws Exception {
        ServiceHelper.buildService(producer);
    }

    @Override
    protected void doInit() throws Exception {
        ServiceHelper.initService(producer);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(producer);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producer);
    }

    @Override
    protected void doSuspend() throws Exception {
        ServiceHelper.suspendService(producer);
    }

    @Override
    protected void doResume() throws Exception {
        ServiceHelper.resumeService(producer);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(producer);
    }

    @Override
    public boolean isSingleton() {
        return producer.isSingleton();
    }

    @Override
    public String toString() {
        return producer.toString();
    }
}
