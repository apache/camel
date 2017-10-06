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
package org.apache.camel.component.connector;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * Connector {@link Producer} which is capable of performing before and after custom processing
 * via the {@link Pipeline }while processing (ie sending the message).
 */
public class ConnectorProducer extends DefaultAsyncProducer {

    private final AsyncProcessor processor;

    public ConnectorProducer(final Endpoint endpoint, final Processor processor) {
        super(endpoint);
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        return processor.process(exchange, callback);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startServices(processor);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processor);
    }

    @Override
    protected void doSuspend() throws Exception {
        ServiceHelper.suspendService(processor);
    }

    @Override
    protected void doResume() throws Exception {
        ServiceHelper.resumeService(processor);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(processor);
    }
}
