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

package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.OpenShiftClient;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class KubernetesClientTest {

    private static final Logger log = LoggerFactory.getLogger(KubernetesClientTest.class);

    @Test
    public void shouldHaveOpenshiftClient() {
        try (KubernetesClient client = KubernetesHelper.getKubernetesClient()) {
            OpenShiftClient openShiftClient = client.adapt(OpenShiftClient.class);
            try {
                openShiftClient.projects().list().getItems().forEach(project -> {
                    log.debug("Project: {}", project.getMetadata().getName());
                });
                log.info("OpenShiftClient is authenticated and working properly.");
            } catch (KubernetesClientException e) {
                log.debug("OpenShiftClient is not authenticated: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.debug("Cannot construct OpenShiftClient: {}", e.getMessage());
        }
    }
}
