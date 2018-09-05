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
import org.apache.camel.component.kubernetes.producer.KubernetesNamespacesProducerTest;
import org.junit.Ignore;

@Ignore
@Deprecated
public class DeprecatedKubernetesNamespacesProducerTest extends KubernetesNamespacesProducerTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list")
                        .toF("kubernetes://%s?oauthToken=%s&category=namespaces&operation=listNamespaces",
                                host, authToken);
                from("direct:listByLabels")
                        .toF("kubernetes://%s?oauthToken=%s&category=namespaces&operation=listNamespacesByLabels",
                                host, authToken);
                from("direct:getNs")
                        .toF("kubernetes://%s?oauthToken=%s&category=namespaces&operation=getNamespace",
                                host, authToken);
                from("direct:createNamespace")
                        .toF("kubernetes://%s?oauthToken=%s&category=namespaces&operation=createNamespace",
                                host, authToken);
                from("direct:deleteNamespace")
                        .toF("kubernetes://%s?oauthToken=%s&category=namespaces&operation=deleteNamespace",
                                host, authToken);
            }
        };
    }
}
