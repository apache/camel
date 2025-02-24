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
package org.apache.camel.component.kubernetes.properties;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class SecretReloadTest extends CamelTestSupport {
    @BindToRegistry
    KubernetesClient client = new KubernetesClientBuilder().build();

    @Test
    void contextReloadDoesNotCloseAutowiredKubernetesClient() throws Exception {
        assertFalse(client.getHttpClient().isClosed());
        context.addService(new DefaultContextReloadStrategy());
        ContextReloadStrategy reload = context.hasService(ContextReloadStrategy.class);
        reload.onReload(this);
        assertFalse(client.getHttpClient().isClosed());
    }
}
