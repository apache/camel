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
package org.apache.camel.http.common;

import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.impl.DefaultConsumer;

public class HttpConsumer extends DefaultConsumer implements Suspendable {
    private volatile boolean suspended;
    private boolean traceEnabled;
    private boolean optionsEnabled;

    public HttpConsumer(HttpCommonEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        if (endpoint.isTraceEnabled()) {
            setTraceEnabled(true);
        }
        if (endpoint.isOptionsEnabled()) {
            setOptionsEnabled(true);
        }
    }

    @Override
    public HttpCommonEndpoint getEndpoint() {
        return (HttpCommonEndpoint)super.getEndpoint();
    }

    public HttpBinding getBinding() {
        return getEndpoint().getHttpBinding();
    }

    public String getPath() {
        return getEndpoint().getPath();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        getEndpoint().connect(this);
        suspended = false;
    }

    @Override
    protected void doStop() throws Exception {
        suspended = false;
        getEndpoint().disconnect(this);
        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        suspended = true;
        super.doSuspend();
    }

    @Override
    protected void doResume() throws Exception {
        suspended = false;
        super.doResume();
    }

    public boolean isSuspended() {
        return suspended;
    }

    public boolean isTraceEnabled() {
        return this.traceEnabled;
    }

    public void setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    public boolean isOptionsEnabled() {
        return optionsEnabled;
    }

    public void setOptionsEnabled(boolean optionsEnabled) {
        this.optionsEnabled = optionsEnabled;
    }
}
