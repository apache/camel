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
import org.apache.camel.component.kubernetes.producer.KubernetesServicesProducerTest;
import org.junit.Ignore;

@Ignore
@Deprecated
public class DeprecatedKubernetesServicesProducerTest extends KubernetesServicesProducerTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list")
                        .toF("kubernetes://%s?oauthToken=%s&category=services&operation=listServices",
                                host, authToken);
                from("direct:listByLabels")
                        .toF("kubernetes://%s?oauthToken=%s&category=services&operation=listServicesByLabels",
                                host, authToken);
                from("direct:getServices")
                        .toF("kubernetes://%s?oauthToken=%s&category=services&operation=getService",
                                host, authToken);
                from("direct:createService")
                        .toF("kubernetes://%s?oauthToken=%s&category=services&operation=createService",
                                host, authToken);
                from("direct:deleteService")
                        .toF("kubernetes://%s?oauthToken=%s&category=services&operation=deleteService",
                                host, authToken);
            }
        };
    }
}
