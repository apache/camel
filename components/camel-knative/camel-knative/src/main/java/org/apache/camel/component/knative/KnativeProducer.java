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
package org.apache.camel.component.knative;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.service.ServiceHelper;

public class KnativeProducer extends DefaultAsyncProducer {
    final AsyncProcessor processor;

    public KnativeProducer(Endpoint endpoint, Processor processor, Processor... processors) {
        super(endpoint);

        List<Processor> elements = new ArrayList<>(1 + processors.length);
        elements.add(processor);

        for (Processor p : processors) {
            elements.add(p);
        }

        Processor pipeline = Pipeline.newInstance(endpoint.getCamelContext(), elements);

        this.processor = AsyncProcessorConverterHelper.convert(pipeline);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        return processor.process(exchange, callback);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(processor);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(processor);
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
        ServiceHelper.stopAndShutdownService(processor);
    }
}
