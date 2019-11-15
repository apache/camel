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
package org.apache.camel.component.openshift.producer;

import java.util.List;

import io.fabric8.kubernetes.api.model.APIGroupListBuilder;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigListBuilder;
import io.fabric8.openshift.client.NamespacedOpenShiftClient;
import io.fabric8.openshift.client.server.mock.OpenShiftServer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.Rule;
import org.junit.Test;

public class OpenshiftBuildConfigsProducerTest extends KubernetesTestSupport {

    @Rule
    public OpenShiftServer server = new OpenShiftServer();

    @BindToRegistry("client")
    public NamespacedOpenShiftClient loadClient() throws Exception {
        server.expect().withPath("/apis/build.openshift.io/v1/namespaces/test/buildconfigs").andReturn(200, new BuildConfigListBuilder().build()).once();

        server.expect().withPath("/apis").andReturn(200, new APIGroupListBuilder().addNewGroup().withApiVersion("v1").withName("autoscaling.k8s.io").endGroup().addNewGroup()
            .withApiVersion("v1").withName("security.openshift.io").endGroup().build()).always();

        server.expect().withPath("/apis/build.openshift.io/v1/namespaces/test/buildconfigs")
            .andReturn(200, new BuildConfigListBuilder().addNewItem().and().addNewItem().and().build()).once();

        server.expect().withPath("/apis/build.openshift.io/v1/buildconfigs")
            .andReturn(200, new BuildConfigListBuilder().addNewItem().and().addNewItem().and().addNewItem().and().build()).once();

        return server.getOpenshiftClient();
    }

    @Test
    public void listTest() throws Exception {
        List<BuildConfig> result = template.requestBody("direct:list", "", List.class);

        assertEquals(3, result.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").to("openshift-build-configs:///?operation=listBuildConfigs&kubernetesClient=#client");
                from("direct:listByLabels").to("openshift-build-configs:///?kubernetesClient=#client&operation=listBuildConfigsByLabels");
            }
        };
    }
}
