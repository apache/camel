/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.support;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.Exchange;
import org.apache.camel.support.service.ServiceHelper;

/**
 * A {@link org.apache.camel.Producer} which is started lazy, on the first message being processed.
 */
public final class LazyStartProducer extends DefaultAsyncProducer {

    private final AsyncProducer delegate;

    public LazyStartProducer(AsyncProducer producer) {
        super(producer.getEndpoint());
        this.delegate = producer;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (!ServiceHelper.isStarted(delegate)) {
            try {
                ServiceHelper.startService(delegate);
            } catch (Throwable e) {
                exchange.setException(e);
                return true;
            }
        }
        return delegate.process(exchange, callback);
    }

    @Override
    public boolean isSingleton() {
        return delegate.isSingleton();
    }

    @Override
    protected void doInit() throws Exception {
        ServiceHelper.initService(delegate);
    }

    @Override
    protected void doStart() throws Exception {
        // noop as we dont want to start the delegate but its started on the first message processed
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(delegate);
    }

    @Override
    protected void doSuspend() throws Exception {
        ServiceHelper.suspendService(delegate);
    }

    @Override
    protected void doResume() throws Exception {
        ServiceHelper.resumeService(delegate);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(delegate);
    }
}
