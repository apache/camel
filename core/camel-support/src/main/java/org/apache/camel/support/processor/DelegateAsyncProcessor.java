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
package org.apache.camel.support.processor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.DelegateProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.service.ServiceHelper;

/**
 * A Delegate pattern which delegates processing to a nested {@link AsyncProcessor} which can
 * be useful for implementation inheritance when writing an {@link org.apache.camel.spi.Policy}
 * <p/>
 * <b>Important:</b> This implementation <b>does</b> support the asynchronous routing engine.
 * If you are implementing a EIP pattern please use this as the delegate.
 * @see DelegateSyncProcessor
 * @see DelegateProcessor
 */
public class DelegateAsyncProcessor extends AsyncProcessorSupport implements DelegateProcessor, Navigate<Processor> {
    protected AsyncProcessor processor;

    public DelegateAsyncProcessor() {
    }

    public DelegateAsyncProcessor(AsyncProcessor processor) {
        if (processor == this) {
            throw new IllegalArgumentException("Recursive DelegateAsyncProcessor!");
        }
        this.processor = processor;
    }

    public DelegateAsyncProcessor(Processor processor) {
        this(AsyncProcessorConverterHelper.convert(processor));
    }

    @Override
    public String toString() {
        return "DelegateAsync[" + processor + "]";
    }

    @Override
    public AsyncProcessor getProcessor() {
        return processor;
    }

    public void setProcessor(AsyncProcessor processor) {
        this.processor = processor;
    }

    public void setProcessor(Processor processor) {
        this.processor = AsyncProcessorConverterHelper.convert(processor);
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
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(processor);
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        return processor.process(exchange, callback);
    }

    @Override
    public boolean hasNext() {
        return processor != null;
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<>(1);
        answer.add(processor);
        return answer;
    }

}
