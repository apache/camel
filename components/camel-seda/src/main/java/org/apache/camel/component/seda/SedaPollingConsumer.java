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
package org.apache.camel.component.seda;

import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.IsSingleton;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.PollingConsumerSupport;

public class SedaPollingConsumer extends PollingConsumerSupport implements IsSingleton {

    public SedaPollingConsumer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public SedaEndpoint getEndpoint() {
        return (SedaEndpoint) super.getEndpoint();
    }

    @Override
    public Processor getProcessor() {
        return null;
    }

    @Override
    public Exchange receive() {
        try {
            return getEndpoint().getQueue().take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public Exchange receiveNoWait() {
        return getEndpoint().getQueue().poll();
    }

    @Override
    public Exchange receive(long timeout) {
        try {
            return getEndpoint().getQueue().poll(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
