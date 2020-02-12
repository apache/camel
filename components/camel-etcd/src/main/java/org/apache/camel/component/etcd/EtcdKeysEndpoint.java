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
package org.apache.camel.component.etcd;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;

@UriEndpoint(firstVersion = "2.18.0", scheme = "etcd-keys", title = "Etcd Keys", producerOnly = true,
        syntax = "etcd-keys:path", label = "clustering,database")
public class EtcdKeysEndpoint extends AbstractEtcdEndpoint {

    public EtcdKeysEndpoint(
            String uri, AbstractEtcdComponent component, EtcdConfiguration configuration, String path) {
        super(uri, component, configuration, path);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new EtcdKeysProducer(this, getConfiguration(), getPath());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new IllegalArgumentException("Consumer not enabled for " + getPath());
    }
}
