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
package org.apache.camel.component.atmos;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.util.URISupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class AtmosComponentTest {

    private static final String FAKE_REMOTE_PATH = "/remote";
    private static final String FAKE_SECRET = "fake-secret";
    private static final String FAKE_TOKEN = "fake-token";
    private static final String FAKE_URI = "http://fake/uri";

    @Mock
    private CamelContext context;

    @Test
    public void testComponentOptions() throws Exception {
        AtmosComponent component = new AtmosComponent(context);
        component.setFullTokenId(FAKE_TOKEN);
        component.setSecretKey(FAKE_SECRET);
        component.setSslValidation(false);
        component.setUri(FAKE_URI);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("remotePath", FAKE_REMOTE_PATH);

        AtmosEndpoint endpoint = component.createEndpoint("atmos://foo?remotePath=/remote", "foo/get", parameters);
        AtmosConfiguration configuration = endpoint.getConfiguration();

        assertEquals(FAKE_TOKEN, configuration.getFullTokenId());
        assertEquals(FAKE_SECRET, configuration.getSecretKey());
        assertEquals(false, configuration.isEnableSslValidation());
        assertEquals(FAKE_URI, configuration.getUri());
    }

    @Test
    public void testUriParamsOverrideComponentOptions() throws Exception {
        AtmosComponent component = new AtmosComponent(context);
        component.setFullTokenId("fakeTokenToBeOverridden");
        component.setSecretKey("fakeSecretToBeOverridden");
        component.setSslValidation(true);
        component.setUri("http://fake/uri/to/be/overridden");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("remotePath", FAKE_REMOTE_PATH);
        parameters.put("fullTokenId", FAKE_TOKEN);
        parameters.put("secretKey", FAKE_SECRET);
        parameters.put("enableSslValidation", false);
        parameters.put("uri", FAKE_URI);

        String uri = URISupport.appendParametersToURI("atmos://foo", parameters);
        AtmosEndpoint endpoint = component.createEndpoint(uri, "foo/get", parameters);
        AtmosConfiguration configuration = endpoint.getConfiguration();

        assertEquals(FAKE_TOKEN, configuration.getFullTokenId());
        assertEquals(FAKE_SECRET, configuration.getSecretKey());
        assertEquals(false, configuration.isEnableSslValidation());
        assertEquals(FAKE_URI, configuration.getUri());
    }
}
