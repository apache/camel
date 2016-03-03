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
package org.apache.camel.component.etcd;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

public class EtcdWatchEndpoint extends AbstractEtcdEndpoint {

    public EtcdWatchEndpoint(
        String uri, EtcdComponent component, EtcdConfiguration configuration, EtcdNamespace namespace, String path) {
        super(uri, component, configuration, namespace, path);
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new IllegalArgumentException("Producer not enabled for " + getPath());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        EtcdWatchConsumer consumer = new EtcdWatchConsumer(this, processor, getConfiguration(), getNamespace(), getPath());
        configureConsumer(consumer);
        return consumer;
    }
}
