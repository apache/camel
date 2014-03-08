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
package org.apache.camel.component.sjms.support;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.SynchronousDelegateProducer;

/**
 *
 */
public class MyAsyncEndpoint extends DefaultEndpoint {

    private String reply;
    private long delay = 25;
    private int failFirstAttempts;

    public MyAsyncEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public Producer createProducer() throws Exception {
        Producer answer = new MyAsyncProducer(this);
        if (isSynchronous()) {
            // force it to be synchronously
            return new SynchronousDelegateProducer(answer);
        } else {
            return answer;
        }
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    public boolean isSingleton() {
        return false;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public int getFailFirstAttempts() {
        return failFirstAttempts;
    }

    public void setFailFirstAttempts(int failFirstAttempts) {
        this.failFirstAttempts = failFirstAttempts;
    }
}
