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
package org.apache.camel.component.hazelcast.multimap;

import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.hazelcast.HazelcastCommand;
import org.apache.camel.component.hazelcast.HazelcastDefaultComponent;
import org.apache.camel.component.hazelcast.HazelcastDefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;

/**
 * The hazelcast-multimap component is used to to access <a href="http://www.hazelcast.com/">Hazelcast</a> distributed multimap.
 */
@UriEndpoint(firstVersion = "2.7.0", scheme = "hazelcast-multimap", title = "Hazelcast Multimap", syntax = "hazelcast-multimap:cacheName", label = "cache,datagrid")
public class HazelcastMultimapEndpoint extends HazelcastDefaultEndpoint {

    public HazelcastMultimapEndpoint(HazelcastInstance hazelcastInstance, String uri, String cacheName, HazelcastDefaultComponent component) {
        super(hazelcastInstance, uri, component, cacheName);
        setCommand(HazelcastCommand.multimap);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HazelcastMultimapConsumer answer = new HazelcastMultimapConsumer(hazelcastInstance, this, processor, cacheName);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HazelcastMultimapProducer(hazelcastInstance, this, cacheName);
    }

}
