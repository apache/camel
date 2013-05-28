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
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ServiceHelper;

/**
 * A Delegate pattern which delegates synchronous processing to a nested {@link org.apache.camel.Processor} which can
 * be useful for implementation inheritance when writing an {@link org.apache.camel.spi.Policy}
 * <p/>
 * <b>Important:</b> This implementation <b>does</b> support the asynchronous routing engine, <b>only</b>.
 * if the logic in the {@link #process(org.apache.camel.Exchange)} does not invoke EIPs; as it forces using
 * synchronous processing during the {@link #process(org.apache.camel.Exchange)} method call.
 * If you are implementing a EIP pattern please use this as the delegate, for simple EIPs.
 *
 * @version
 * @see DelegateAsyncProcessor
 * @see org.apache.camel.processor.DelegateProcessor
 */
public class DelegateSyncProcessor extends ServiceSupport implements org.apache.camel.DelegateProcessor, AsyncProcessor, Navigate<Processor> {
    protected Processor processor;

    public DelegateSyncProcessor(Processor processor) {
        this.processor = processor;
    }

    @Override
    public String toString() {
        return "DelegateSync[" + processor + "]";
    }

    public Processor getProcessor() {
        return processor;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // force calling the sync method
        try {
            processor.process(exchange);
        } catch (Throwable e) {
            // must catch throwable so we catch all
            exchange.setException(e);
        } finally {
            // we are bridging a sync processor as async so callback with true
            callback.done(true);
        }
        return true;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        processor.process(exchange);
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
        List<Processor> answer = new ArrayList<Processor>(1);
        answer.add(processor);
        return answer;
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(processor);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processor);
    }
}
