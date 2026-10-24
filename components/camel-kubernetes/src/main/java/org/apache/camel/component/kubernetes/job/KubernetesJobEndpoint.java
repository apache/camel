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
package org.apache.camel.component.kubernetes.job;

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

import static org.apache.camel.component.kubernetes.KubernetesConstants.SCHEME_JOB;

/**
 * Perform operations on Kubernetes Jobs.
 */
@UriEndpoint(firstVersion = "2.23.0", scheme = SCHEME_JOB, title = "Kubernetes Job", syntax = "kubernetes-job:masterUrl",
             category = { Category.CONTAINER, Category.CLOUD }, producerOnly = true, headersClass = KubernetesConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: the operations take their input from headers, such as the name of the Job in "
                                                + "CamelKubernetesJobName and its JobSpec in CamelKubernetesJobSpec"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.POJO, Carrier.NULL, Carrier.UNCHANGED },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listJob", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "listJobByLabels",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "getJob",
                                                          carriers = { Carrier.POJO, Carrier.NULL }),
                                           @BodyType.When(option = "operation", value = "createJob", carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.batch.v1.Job"),
                                           @BodyType.When(option = "operation", value = "updateJob", carriers = Carrier.POJO,
                                                          javaType = "io.fabric8.kubernetes.api.model.batch.v1.Job"),
                                           @BodyType.When(option = "operation", value = "deleteJob",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "A List of the Jobs for the list operations, the Job of get, create and update (null "
                                                 + "when getJob finds none), and the body as it was after deleteJob"))
public class KubernetesJobEndpoint extends AbstractKubernetesEndpoint {

    public KubernetesJobEndpoint(String uri, KubernetesJobComponent component, KubernetesConfiguration config) {
        super(uri, component, config);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KubernetesJobProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new IllegalArgumentException("The kubernetes-job doesn't support consumer");
    }

}
