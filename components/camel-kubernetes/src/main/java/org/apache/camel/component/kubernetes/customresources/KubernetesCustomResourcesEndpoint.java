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
package org.apache.camel.component.kubernetes.customresources;

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

import static org.apache.camel.component.kubernetes.KubernetesConstants.SCHEME_CUSTOM_RESOURCES;

/**
 * Perform operations on Kubernetes Custom Resources and get notified on Deployment changes.
 */
@UriEndpoint(firstVersion = "3.7.0", scheme = SCHEME_CUSTOM_RESOURCES, title = "Kubernetes Custom Resources",
             syntax = "kubernetes-custom-resources:masterUrl", category = { Category.CONTAINER, Category.CLOUD },
             headersClass = KubernetesConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String", format = "json",
                                   description = "The custom resource that changed as JSON text, with the action (ADDED, MODIFIED or"
                                                 + " DELETED) in the CamelKubernetesCRDEventAction header"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: the operations take their input from headers, such as the name of the"
                                                + " instance in CamelKubernetesCRDInstanceName and, for createCustomResource and"
                                                + " updateCustomResource, the instance as JSON text in CamelKubernetesCRDInstance"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.MAP, Carrier.POJO, Carrier.UNCHANGED },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listCustomResources",
                                                          carriers = Carrier.LIST,
                                                          javaType = "org.apache.camel.util.json.JsonArray"),
                                           @BodyType.When(option = "operation", value = "listCustomResourcesByLabels",
                                                          carriers = Carrier.LIST,
                                                          javaType = "org.apache.camel.util.json.JsonArray"),
                                           @BodyType.When(option = "operation", value = "getCustomResource",
                                                          carriers = Carrier.MAP,
                                                          javaType = "org.apache.camel.util.json.JsonObject"),
                                           @BodyType.When(option = "operation", value = "createCustomResource",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.GenericKubernetesResource"),
                                           @BodyType.When(option = "operation", value = "updateCustomResource",
                                                          carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.GenericKubernetesResource"),
                                           @BodyType.When(option = "operation", value = "deleteCustomResource",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "A JsonArray of the GenericKubernetesResource of the instances for the list"
                                                 + " operations, a JsonObject of the instance for getCustomResource, and the"
                                                 + " GenericKubernetesResource of create and update (an empty one when it already"
                                                 + " exists). deleteCustomResource leaves the body as it was, with whether it deleted"
                                                 + " the instance in the CamelKubernetesDeleteResult header"))
public class KubernetesCustomResourcesEndpoint extends AbstractKubernetesEndpoint {

    public KubernetesCustomResourcesEndpoint(String uri, KubernetesCustomResourcesComponent component,
                                             KubernetesConfiguration config) {
        super(uri, component, config);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KubernetesCustomResourcesProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new KubernetesCustomResourcesConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

}
