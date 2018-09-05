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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class KubernetesComponent extends AbstractKubernetesComponent {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesComponent.class);

    protected AbstractKubernetesEndpoint doCreateEndpoint(String uri, String remaining, KubernetesConfiguration config) throws Exception {
        LOG.warn("The syntax 'kubernetes://{}' has been deprecated. Use 'kubernetes-{}://{}' instead.", remaining, config.getCategory(), remaining);
        KubernetesEndpoint endpoint = new KubernetesEndpoint(uri, this, config);
        return endpoint;
    }
}
