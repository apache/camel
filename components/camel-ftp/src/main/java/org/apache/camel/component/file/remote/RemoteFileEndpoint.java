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
package org.apache.camel.component.file.remote;

import java.io.ByteArrayOutputStream;

import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.ExchangePattern;

public abstract class RemoteFileEndpoint<T extends RemoteFileExchange> extends ScheduledPollEndpoint<T> {
    private RemoteFileBinding binding;
    private RemoteFileConfiguration configuration;

    public RemoteFileEndpoint(String uri, RemoteFileComponent component, RemoteFileConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    protected RemoteFileBinding createRemoteFileBinding() {
        return new RemoteFileBinding();
    }

    public T createExchange() {
        return (T) new RemoteFileExchange(getContext(), getExchangePattern(), getBinding());
    }

    public T createExchange(ExchangePattern pattern) {
        return (T) new RemoteFileExchange(getContext(), pattern, getBinding());
    }

    public T createExchange(String fullFileName, ByteArrayOutputStream outputStream) {
        return (T) new RemoteFileExchange(getContext(), getExchangePattern(), getBinding(), getConfiguration().getHost(), fullFileName, outputStream);
    }

    public RemoteFileBinding getBinding() {
        if (binding == null) {
            binding = createRemoteFileBinding();
        }
        return binding;
    }

    public void setBinding(RemoteFileBinding binding) {
        this.binding = binding;
    }

    public boolean isSingleton() {
        return true;
    }

    public RemoteFileConfiguration getConfiguration() {
        return configuration;
    }
}
