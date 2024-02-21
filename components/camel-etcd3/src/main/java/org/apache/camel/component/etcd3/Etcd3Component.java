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

import java.util.Map;
import java.util.Optional;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

@Component("etcd3")
public class Etcd3Component extends DefaultComponent {

    @Metadata
    private Etcd3Configuration configuration = new Etcd3Configuration();

    public Etcd3Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration.
     */
    public void setConfiguration(Etcd3Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // path must start with leading slash
        String path = ObjectHelper.isEmpty(remaining) ? "/" : remaining;
        if (!path.startsWith("/")) {
            path = String.format("/%s", path);
        }

        Endpoint endpoint = new Etcd3Endpoint(uri, this, loadConfiguration(), path);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    private Etcd3Configuration loadConfiguration() {
        return Optional.ofNullable(this.configuration).orElseGet(Etcd3Configuration::new).copy();
    }
}
