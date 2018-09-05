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
package org.apache.camel.component.kubernetes.deprecated;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.consumer.KubernetesReplicationControllersConsumerTest;

@Deprecated
public class DeprecatedKubernetesReplicationControllersConsumerTest extends KubernetesReplicationControllersConsumerTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list").toF(
                        "kubernetes://%s?oauthToken=%s&category=replicationControllers&operation=listReplicationControllers",
                        host, authToken);
                from("direct:listByLabels").toF(
                        "kubernetes://%s?oauthToken=%s&category=replicationControllers&operation=listReplicationControllersByLabels",
                        host, authToken);
                from("direct:getReplicationController").toF(
                        "kubernetes://%s?oauthToken=%s&category=replicationControllers&operation=getReplicationController",
                        host, authToken);
                from("direct:createReplicationController").toF(
                        "kubernetes://%s?oauthToken=%s&category=replicationControllers&operation=createReplicationController",
                        host, authToken);
                from("direct:deleteReplicationController").toF(
                        "kubernetes://%s?oauthToken=%s&category=replicationControllers&operation=deleteReplicationController",
                        host, authToken);
                fromF("kubernetes://%s?oauthToken=%s&category=replicationControllers&resourceName=wildfly", host, authToken)
                        .process(new KubernertesProcessor()).to(mockResultEndpoint);
            }
        };
    }

}
