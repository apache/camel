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
package org.apache.camel.component.kubernetes.resources_quota;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.kubernetes.AbstractKubernetesEndpoint;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;

import static org.apache.camel.component.kubernetes.KubernetesConstants.SCHEME_RESOURCES_QUOTA;

/**
 * Perform operations on Kubernetes Resources Quotas.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = SCHEME_RESOURCES_QUOTA, title = "Kubernetes Resources Quota",
             syntax = "kubernetes-resources-quota:masterUrl", producerOnly = true,
             category = { Category.CONTAINER, Category.CLOUD }, headersClass = KubernetesConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: the operation (the operation option, or else the"
                                                + " CamelKubernetesOperation header) takes its input from headers: the"
                                                + " namespace in CamelKubernetesNamespaceName, the name in"
                                                + " CamelKubernetesResourcesQuotaName, the labels in"
                                                + " CamelKubernetesResourcesQuotaLabels and the ResourceQuotaSpec in"
                                                + " CamelKubernetesResourceQuotaSpec"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.NULL, Carrier.VALUE },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listResourcesQuota",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<io.fabric8.kubernetes.api.model.ResourceQuota>"),
                                           @BodyType.When(option = "operation", value = "listResourcesQuotaByLabels",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<io.fabric8.kubernetes.api.model.ResourceQuota>"),
                                           @BodyType.When(option = "operation", value = "getResourceQuota",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "io.fabric8.kubernetes.api.model.ResourceQuota"),
                                           @BodyType.When(option = "operation", value = "createResourceQuota",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.ResourceQuota"),
                                           @BodyType.When(option = "operation", value = "updateResourceQuota",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.ResourceQuota"),
                                           @BodyType.When(option = "operation", value = "deleteResourceQuota",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean") },
                                   description = "A List of ResourceQuota for listResourcesQuota and"
                                                 + " listResourcesQuotaByLabels; the ResourceQuota for getResourceQuota (null"
                                                 + " when not found), createResourceQuota and updateResourceQuota; a Boolean"
                                                 + " for deleteResourceQuota, false when not found"))
public class KubernetesResourcesQuotaEndpoint extends AbstractKubernetesEndpoint {

    public KubernetesResourcesQuotaEndpoint(String uri, KubernetesResourcesQuotaComponent component,
                                            KubernetesConfiguration config) {
        super(uri, component, config);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KubernetesResourcesQuotaProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new IllegalArgumentException("The kubernetes-resources-quota doesn't support consumer");
    }

}
