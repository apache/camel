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
package org.apache.camel.component.kubernetes.persistent_volumes;

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

import static org.apache.camel.component.kubernetes.KubernetesConstants.SCHEME_PERSISTENT_VOLUMES;

/**
 * Perform operations on Kubernetes Persistent Volumes and get notified on Persistent Volume changes.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = SCHEME_PERSISTENT_VOLUMES, title = "Kubernetes Persistent Volume",
             syntax = "kubernetes-persistent-volumes:masterUrl",
             producerOnly = true, category = { Category.CONTAINER, Category.CLOUD },
             headersClass = KubernetesConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: the operation (the operation option, or else the"
                                                + " CamelKubernetesOperation header) takes its input from headers: the name"
                                                + " in CamelKubernetesPersistentVolumeName and the labels in"
                                                + " CamelKubernetesPersistentVolumesLabels"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.NULL },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listPersistentVolumes",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<io.fabric8.kubernetes.api.model.PersistentVolume>"),
                                           @BodyType.When(option = "operation", value = "listPersistentVolumesByLabels",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<io.fabric8.kubernetes.api.model.PersistentVolume>"),
                                           @BodyType.When(option = "operation", value = "getPersistentVolume",
                                                          carriers = { Carrier.POJO, Carrier.NULL },
                                                          javaType = "io.fabric8.kubernetes.api.model.PersistentVolume") },
                                   description = "A List of PersistentVolume for listPersistentVolumes and"
                                                 + " listPersistentVolumesByLabels; the PersistentVolume for"
                                                 + " getPersistentVolume (null when not found)"))
public class KubernetesPersistentVolumesEndpoint extends AbstractKubernetesEndpoint {

    public KubernetesPersistentVolumesEndpoint(String uri, KubernetesPersistentVolumesComponent component,
                                               KubernetesConfiguration config) {
        super(uri, component, config);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KubernetesPersistentVolumesProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new IllegalArgumentException("The kubernetes-persistent-volumes doesn't support consumer");
    }

}
