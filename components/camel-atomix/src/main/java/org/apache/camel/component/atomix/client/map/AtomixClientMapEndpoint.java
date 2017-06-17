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
package org.apache.camel.component.atomix.client.map;

import org.apache.camel.Producer;
import org.apache.camel.component.atomix.client.AbstractAtomixClientEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

@UriEndpoint(firstVersion = "2.20.0", scheme = "atomix-map", title = "Atomix Map", syntax = "atomix-map:mapName", producerOnly = true, label = "clustering")
class AtomixClientMapEndpoint extends AbstractAtomixClientEndpoint<AtomixClientMapComponent, AtomixClientMapConfiguration> {

    @UriPath(description = "The distributed map name")
    @Metadata(required = "true")
    private final String mapName;

    public AtomixClientMapEndpoint(String uri, AtomixClientMapComponent component, AtomixClientMapConfiguration configuration, String mapName) {
        super(uri, component, configuration);

        this.mapName = mapName;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new AtomixClientMapProducer(this, mapName);
    }

    /*
    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new AtomixClientMapConsumer(this, processor);
    }
    */

    public String getMapName() {
        return mapName;
    }
}
