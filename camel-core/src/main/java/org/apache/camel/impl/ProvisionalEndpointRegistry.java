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
package org.apache.camel.impl;

import java.util.HashMap;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.EndpointRegistry;

/**
 * A provisional (temporary) {@link EndpointRegistry} that is only used during startup of Apache Camel to
 * make starting Camel faster while {@link org.apache.camel.util.LRUCacheFactory} is warming up etc.
 */
class ProvisionalEndpointRegistry extends HashMap<EndpointKey, Endpoint> implements EndpointRegistry<EndpointKey> {

    @Override
    public void start() throws Exception {
        // noop
    }

    @Override
    public void stop() throws Exception {
        // noop
    }

    @Override
    public int staticSize() {
        return 0;
    }

    @Override
    public int dynamicSize() {
        return 0;
    }

    @Override
    public int getMaximumCacheSize() {
        return 0;
    }

    @Override
    public void purge() {
        // noop
    }

    @Override
    public boolean isStatic(String key) {
        return false;
    }

    @Override
    public boolean isDynamic(String key) {
        return false;
    }

    @Override
    public void cleanUp() {
        // noop
    }
}
