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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ServiceAccount;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.apache.camel.component.kubernetes.producer.KubernetesServiceAccountsProducerTest;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.codec.binary.Base64;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
@Deprecated
public class DeprecatedKubernetesServiceAccountsProducerTest extends KubernetesServiceAccountsProducerTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:list")
                        .toF("kubernetes://%s?oauthToken=%s&category=serviceAccounts&operation=listServiceAccounts",
                                host, authToken);
                from("direct:listByLabels")
                        .toF("kubernetes://%s?oauthToken=%s&category=serviceAccounts&operation=listServiceAccountsByLabels",
                                host, authToken);
                from("direct:getServices")
                        .toF("kubernetes://%s?oauthToken=%s&category=serviceAccounts&operation=getServiceAccount",
                                host, authToken);
                from("direct:create")
                        .toF("kubernetes://%s?oauthToken=%s&category=serviceAccounts&operation=createServiceAccount",
                                host, authToken);
                from("direct:delete")
                        .toF("kubernetes://%s?oauthToken=%s&category=serviceAccounts&operation=deleteServiceAccount",
                                host, authToken);
            }
        };
    }
}
