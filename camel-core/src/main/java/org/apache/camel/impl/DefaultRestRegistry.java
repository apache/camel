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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.StaticService;
import org.apache.camel.spi.RestRegistry;

public class DefaultRestRegistry extends ServiceSupport implements StaticService, RestRegistry {

    private final Map<Consumer, RestService> registry = new LinkedHashMap<Consumer, RestService>();

    public void addRestService(Consumer consumer, String url, String path, String verb, String consumes, String produces) {
        RestServiceEntry entry = new RestServiceEntry(consumer, url, path, verb, consumes, produces);
        registry.put(consumer, entry);
    }

    public void removeRestService(Consumer consumer) {
        registry.remove(consumer);
    }

    @Override
    public List<RestRegistry.RestService> listAllRestServices() {
        return new ArrayList<RestService>(registry.values());
    }

    @Override
    public int size() {
        return registry.size();
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        registry.clear();
    }

    /**
     * Represents a rest service
     */
    private final class RestServiceEntry implements RestService {

        private final Consumer consumer;
        private final String url;
        private final String path;
        private final String verb;
        private final String consumes;
        private final String produces;

        private RestServiceEntry(Consumer consumer, String url, String path, String verb, String consumes, String produces) {
            this.consumer = consumer;
            this.url = url;
            this.path = path;
            this.verb = verb;
            this.consumes = consumes;
            this.produces = produces;
        }

        public Consumer getConsumer() {
            return consumer;
        }

        public String getUrl() {
            return url;
        }

        public String getPath() {
            return path;
        }

        public String getVerb() {
            return verb;
        }

        public String getConsumes() {
            return consumes;
        }

        public String getProduces() {
            return produces;
        }
    }
}
