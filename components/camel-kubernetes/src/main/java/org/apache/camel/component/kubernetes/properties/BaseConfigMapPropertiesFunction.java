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
package org.apache.camel.component.kubernetes.properties;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * Base for kubernetes {@link PropertiesFunction}.
 */
abstract class BaseConfigMapPropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    private CamelContext camelContext;
    private KubernetesClient client;

    @Override
    protected void doInit() throws Exception {
        if (client == null) {
            client = CamelContextHelper.findSingleByType(camelContext, KubernetesClient.class);
        }
        ObjectHelper.notNull(client, "KubernetesClient must be configured");
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public KubernetesClient getClient() {
        return client;
    }

    public void setClient(KubernetesClient client) {
        this.client = client;
    }

}
