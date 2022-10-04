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
package org.apache.camel.component.etcd3;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Get, set, delete or watch keys in etcd key-value store.
 */
@UriEndpoint(firstVersion = "3.19.0", scheme = "etcd3", title = "Etcd v3",
             syntax = "etcd3:path", category = { Category.CLUSTERING, Category.DATABASE },
             headersClass = Etcd3Constants.class)
public class Etcd3Endpoint extends DefaultEndpoint {

    @UriPath(label = "common", description = "The path the endpoint refers to")
    private final String path;
    @UriParam
    private final Etcd3Configuration configuration;

    public Etcd3Endpoint(String uri, Etcd3Component component, Etcd3Configuration configuration, String path) {
        super(uri, component);
        this.path = path;
        this.configuration = configuration;
    }

    public Etcd3Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public Producer createProducer() {
        return new Etcd3Producer(this, configuration, path);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        return new Etcd3Consumer(this, processor, configuration, path);
    }
}
