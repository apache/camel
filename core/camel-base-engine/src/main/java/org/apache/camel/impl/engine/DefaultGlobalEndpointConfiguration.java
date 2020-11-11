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
package org.apache.camel.impl.engine;

import org.apache.camel.GlobalEndpointConfiguration;

public final class DefaultGlobalEndpointConfiguration implements GlobalEndpointConfiguration {

    private boolean lazyStartProducer;
    private boolean bridgeErrorHandler;
    private boolean basicPropertyBinding;

    @Override
    public boolean isLazyStartProducer() {
        return lazyStartProducer;
    }

    @Override
    public void setLazyStartProducer(boolean lazyStartProducer) {
        this.lazyStartProducer = lazyStartProducer;
    }

    @Override
    public boolean isBridgeErrorHandler() {
        return bridgeErrorHandler;
    }

    @Override
    public void setBridgeErrorHandler(boolean bridgeErrorHandler) {
        this.bridgeErrorHandler = bridgeErrorHandler;
    }

    @Override
    public boolean isBasicPropertyBinding() {
        return basicPropertyBinding;
    }

    @Override
    public void setBasicPropertyBinding(boolean basicPropertyBinding) {
        this.basicPropertyBinding = basicPropertyBinding;
    }
}
