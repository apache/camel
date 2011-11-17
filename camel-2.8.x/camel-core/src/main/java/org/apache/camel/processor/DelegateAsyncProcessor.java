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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * A Delegate pattern which delegates processing to a nested {@link AsyncProcessor} which can
 * be useful for implementation inheritance when writing an {@link org.apache.camel.spi.Policy}
 * <p/>
 * <b>Important:</b> This implementation <b>does</b> support the asynchronous routing engine.
 * If you are implementing a EIP pattern please use this as the delegate.
 *
 * @version 
 * @see org.apache.camel.processor.DelegateProcessor
 */
public class DelegateAsyncProcessor extends ServiceSupport implements AsyncProcessor, Navigate<Processor> {
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
        this(AsyncProcessorTypeConverter.convert(processor));
    }

    @Override
    public String toString() {
        return "DelegateAsync[" + processor + "]";
    }

    public AsyncProcessor getProcessor() {
        return processor;
    }

    public void setProcessor(AsyncProcessor processor) {
        this.processor = processor;
    }

    public void setProcessor(Processor processor) {
        this.processor = AsyncProcessorTypeConverter.convert(processor);
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(processor);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processor);
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        return processNext(exchange, callback);
    }

    protected boolean processNext(Exchange exchange, AsyncCallback callback) {
        if (processor == null) {
            // no processor then we are done
            callback.done(true);
            return true;
        }
        return AsyncProcessorHelper.process(processor, exchange, callback);
    }

    public boolean hasNext() {
        return processor != null;
    }

    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<Processor>(1);
        answer.add(processor);
        return answer;
    }

}
