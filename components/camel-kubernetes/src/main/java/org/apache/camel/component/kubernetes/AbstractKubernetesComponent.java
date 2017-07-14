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
package org.apache.camel.component.kubernetes;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

public abstract class AbstractKubernetesComponent extends DefaultComponent {

    protected Endpoint createEndpoint(String uri, String remaining,
            Map<String, Object> parameters) throws Exception {
        KubernetesConfiguration config = new KubernetesConfiguration();
        setProperties(config, parameters);
        config.setMasterUrl(remaining);
        if (ObjectHelper.isEmpty(config.getMasterUrl())) {
            throw new IllegalArgumentException("Master URL must be specified");
        }
        return doCreateEndpoint(uri, remaining, config);
    }

    protected abstract AbstractKubernetesEndpoint doCreateEndpoint(String uri, String remaining, KubernetesConfiguration config) throws Exception;
}
