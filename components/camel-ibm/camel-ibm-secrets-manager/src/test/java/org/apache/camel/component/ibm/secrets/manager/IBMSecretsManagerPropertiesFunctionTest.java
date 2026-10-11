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
package org.apache.camel.component.ibm.secrets.manager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ibm.cloud.sdk.core.http.Response;
import com.ibm.cloud.sdk.core.http.ServiceCall;
import com.ibm.cloud.sdk.core.http.ServiceCallback;
import com.ibm.cloud.sdk.core.security.NoAuthAuthenticator;
import com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.SecretsManager;
import com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.model.GetSecretByNameTypeOptions;
import com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.model.Secret;
import io.reactivex.Single;
import okhttp3.Protocol;
import okhttp3.Request;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IBMSecretsManagerPropertiesFunctionTest {

    @Test
    public void testSecretGroupIsPerLookup() {
        RecordingSecretsManager client = new RecordingSecretsManager();
        IBMSecretsManagerPropertiesFunction function = new IBMSecretsManagerPropertiesFunction(client);

        assertEquals("groupA/route", function.apply("groupA:route"));
        assertEquals("groupB-user", function.apply("groupB:database#username"));
        // no group must not reuse the group of the previous lookup
        assertEquals("default/route", function.apply("route"));
        assertEquals("groupA/route", function.apply("groupA:route:fallback"));

        assertEquals(List.of("groupA", "groupB", "default", "groupA"), client.groups);
        assertEquals(Set.of("route", "database"), function.getSecrets());
    }

    @Test
    public void testSecretsIsReadOnlyView() {
        IBMSecretsManagerPropertiesFunction function = new IBMSecretsManagerPropertiesFunction(new RecordingSecretsManager());
        function.apply("groupA:route");

        Set<String> secrets = function.getSecrets();
        assertThrows(UnsupportedOperationException.class, () -> secrets.add("other"));

        // the view reflects later lookups
        function.apply("groupA:other");
        assertTrue(secrets.contains("other"));
    }

    /**
     * Returns a secret whose payload and data are derived from the requested secret group and name.
     */
    private static final class RecordingSecretsManager extends SecretsManager {

        private final List<String> groups = new CopyOnWriteArrayList<>();

        RecordingSecretsManager() {
            super("test", new NoAuthAuthenticator());
        }

        @Override
        public ServiceCall<Secret> getSecretByNameType(GetSecretByNameTypeOptions options) {
            String group = options.secretGroupName();
            groups.add(group);
            Secret secret = new TestSecret(group + "/" + options.name(), Map.of("username", group + "-user"));
            okhttp3.Response http = new okhttp3.Response.Builder()
                    .request(new Request.Builder().url("http://localhost/").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .build();
            return new FixedServiceCall<>(new Response<>(secret, http));
        }
    }

    private static final class TestSecret extends Secret {

        TestSecret(String payload, Map<String, Object> data) {
            this.payload = payload;
            this.data = data;
        }
    }

    private static final class FixedServiceCall<T> implements ServiceCall<T> {

        private final Response<T> response;

        FixedServiceCall(Response<T> response) {
            this.response = response;
        }

        @Override
        public ServiceCall<T> addHeader(String name, String value) {
            return this;
        }

        @Override
        public Response<T> execute() {
            return response;
        }

        @Override
        public void enqueue(ServiceCallback<T> callback) {
            callback.onResponse(response);
        }

        @Override
        public Single<Response<T>> reactiveRequest() {
            return Single.just(response);
        }

        @Override
        public void cancel() {
            // noop
        }
    }
}
