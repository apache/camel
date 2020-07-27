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
package org.apache.camel.component;

import java.util.HashMap;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.dsl.MockServerExpectation;
import io.fabric8.mockwebserver.dsl.ReturnOrWebsocketable;
import io.fabric8.mockwebserver.dsl.TimesOnceableOrHttpHeaderable;
import io.fabric8.openshift.client.NamespacedOpenShiftClient;
import io.fabric8.openshift.client.server.mock.OpenShiftMockServer;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class OpenShiftServer implements BeforeEachCallback, AfterEachCallback {

    protected OpenShiftMockServer mock;
    private NamespacedOpenShiftClient client;
    private boolean https;
    private boolean curdMode;

    public OpenShiftServer() {
        this(true, false);
    }

    public OpenShiftServer(boolean https) {
        this(https, false);
    }

    public OpenShiftServer(boolean https, boolean curdMode) {
        this.https = https;
        this.curdMode = curdMode;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        this.mock = this.curdMode
                ? new OpenShiftMockServer(
                        new Context(), new MockWebServer(), new HashMap(), new KubernetesCrudDispatcher(), true)
                : new OpenShiftMockServer(this.https);
        this.mock.init();
        this.client = this.mock.createOpenShiftClient();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        this.mock.destroy();
        this.client.close();
    }

    public KubernetesClient getKubernetesClient() {
        return this.client;
    }

    public NamespacedOpenShiftClient getOpenshiftClient() {
        return this.client;
    }

    public MockServerExpectation expect() {
        return this.mock.expect();
    }

    /** @deprecated */
    @Deprecated
    public <T> void expectAndReturnAsJson(String path, int code, T body) {
        ((TimesOnceableOrHttpHeaderable) ((ReturnOrWebsocketable) this.expect().withPath(path)).andReturn(code, body)).always();
    }

    /** @deprecated */
    @Deprecated
    public void expectAndReturnAsString(String path, int code, String body) {
        ((TimesOnceableOrHttpHeaderable) ((ReturnOrWebsocketable) this.expect().withPath(path)).andReturn(code, body)).always();
    }

    public MockWebServer getMockServer() {
        return this.mock.getServer();
    }

    public RecordedRequest getLastRequest() throws InterruptedException {
        int count = this.mock.getServer().getRequestCount();

        RecordedRequest request;
        for (request = null; count-- > 0; request = this.mock.getServer().takeRequest()) {
        }

        return request;
    }

}
