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
package org.apache.camel.component.kubernetes.ha.utils;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.utils.ResponseProvider;

import okhttp3.mockwebserver.RecordedRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Test server to interact with Kubernetes for locking on a ConfigMap.
 */
public class LockTestServer extends KubernetesMockServer {

    private static final Logger LOG = LoggerFactory.getLogger(LockTestServer.class);

    private boolean refuseRequests;

    private Long delayRequests;

    public LockTestServer(ConfigMapLockSimulator lockSimulator) {

        expect().get().withPath("/api/v1/namespaces/test/configmaps/" + lockSimulator.getConfigMapName()).andReply(new ResponseProvider<Object>() {
            ThreadLocal<Integer> responseCode = new ThreadLocal<>();

            @Override
            public int getStatusCode() {
                return responseCode.get();
            }

            @Override
            public Object getBody(RecordedRequest recordedRequest) {
                delayIfNecessary();
                if (refuseRequests) {
                    responseCode.set(500);
                    return "";
                }

                ConfigMap map = lockSimulator.getConfigMap();
                if (map != null) {
                    responseCode.set(200);
                    return map;
                } else {
                    responseCode.set(404);
                    return "";
                }
            }
        }).always();

        expect().post().withPath("/api/v1/namespaces/test/configmaps").andReply(new ResponseProvider<Object>() {
            ThreadLocal<Integer> responseCode = new ThreadLocal<>();

            @Override
            public int getStatusCode() {
                return responseCode.get();
            }

            @Override
            public Object getBody(RecordedRequest recordedRequest) {
                delayIfNecessary();
                if (refuseRequests) {
                    responseCode.set(500);
                    return "";
                }

                ConfigMap map = convert(recordedRequest);
                if (map == null || map.getMetadata() == null || !lockSimulator.getConfigMapName().equals(map.getMetadata().getName())) {
                    throw new IllegalArgumentException("Illegal configMap received");
                }

                boolean done = lockSimulator.setConfigMap(map, true);
                if (done) {
                    responseCode.set(201);
                    return lockSimulator.getConfigMap();
                } else {
                    responseCode.set(500);
                    return "";
                }
            }
        }).always();

        expect().put().withPath("/api/v1/namespaces/test/configmaps/" + lockSimulator.getConfigMapName()).andReply(new ResponseProvider<Object>() {
            ThreadLocal<Integer> responseCode = new ThreadLocal<>();

            @Override
            public int getStatusCode() {
                return responseCode.get();
            }

            @Override
            public Object getBody(RecordedRequest recordedRequest) {
                delayIfNecessary();
                if (refuseRequests) {
                    responseCode.set(500);
                    return "";
                }

                ConfigMap map = convert(recordedRequest);

                boolean done = lockSimulator.setConfigMap(map, false);
                if (done) {
                    responseCode.set(200);
                    return lockSimulator.getConfigMap();
                } else {
                    responseCode.set(409);
                    return "";
                }
            }
        }).always();

        // Other resources
        expect().get().withPath("/api/v1/namespaces/test/pods").andReturn(200, new PodListBuilder().withNewMetadata().withResourceVersion("1").and().build()).always();
        expect().get().withPath("/api/v1/namespaces/test/pods?resourceVersion=1&watch=true").andUpgradeToWebSocket().open().done().always();
    }


    public boolean isRefuseRequests() {
        return refuseRequests;
    }

    public void setRefuseRequests(boolean refuseRequests) {
        this.refuseRequests = refuseRequests;
    }

    public Long getDelayRequests() {
        return delayRequests;
    }

    public void setDelayRequests(Long delayRequests) {
        this.delayRequests = delayRequests;
    }

    private void delayIfNecessary() {
        if (delayRequests != null) {
            try {
                Thread.sleep(delayRequests);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private ConfigMap convert(RecordedRequest request) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(request.getBody().readByteArray(), ConfigMap.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Erroneous data", e);
        }
    }

}
