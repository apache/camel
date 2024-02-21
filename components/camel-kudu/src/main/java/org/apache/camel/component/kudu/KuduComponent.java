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
package org.apache.camel.component.kudu;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.kudu.client.KuduClient;

/**
 * Represents the component that manages {@link KuduEndpoint}.
 */
@Component("kudu")
public class KuduComponent extends DefaultComponent {
    @Metadata(label = "advanced", autowired = true)
    private KuduClient kuduClient;

    public KuduComponent(CamelContext context) {
        super(context);
    }

    public KuduComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        KuduEndpoint endpoint = new KuduEndpoint(remaining, this);
        if (kuduClient != null) {
            endpoint.setKuduClient(kuduClient);
            endpoint.setUserManagedClient(true);
        }
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public KuduClient getKuduClient() {
        return kuduClient;
    }

    /**
     * To use an existing Kudu client instance, instead of creating a client per endpoint. This allows you to customize
     * various aspects to the client configuration.
     */
    public void setKuduClient(KuduClient kuduClient) {
        this.kuduClient = kuduClient;
    }
}
