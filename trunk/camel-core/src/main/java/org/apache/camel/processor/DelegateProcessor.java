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

import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ServiceHelper;

/**
 * A Delegate pattern which delegates processing to a nested {@link Processor} which can
 * be useful for implementation inheritance when writing an {@link org.apache.camel.spi.Policy}
 * <p/>
 * <b>Important:</b> This implementation does <b>not</b> support the asynchronous routing engine.
 * If you are implementing a EIP pattern please use the {@link org.apache.camel.processor.DelegateAsyncProcessor}
 * instead.
 * 
 * @version 
 * @see org.apache.camel.processor.DelegateAsyncProcessor
 */
public class DelegateProcessor extends ServiceSupport implements org.apache.camel.DelegateProcessor, Processor, Navigate<Processor> {
    protected Processor processor;

    public DelegateProcessor() {
    }

    public DelegateProcessor(Processor processor) {
        if (processor == this) {
            throw new IllegalArgumentException("Recursive DelegateProcessor!");
        }
        this.processor = processor;
    }

    public void process(Exchange exchange) throws Exception {
        processNext(exchange);
    }

    protected void processNext(Exchange exchange) throws Exception {
        if (processor != null) {
            processor.process(exchange);
        }
    }

    @Override
    public String toString() {
        return "Delegate[" + processor + "]";
    }

    public Processor getProcessor() {
        return processor;
    }

    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(processor);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processor);
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
