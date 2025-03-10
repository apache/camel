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
package org.apache.camel.component.tahu;

import java.util.List;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.junit5.TestSupport;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

@SuppressWarnings("unused")
public class TahuConfigurationTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TahuConfigurationTest.class);

    @Test
    public void checkBasicEdgeNodeOptions() throws Exception {
        String uri
                = TahuConstants.EDGE_NODE_SCHEME
                  + "://Basic/EdgeNode?clientId=client1&primaryHostId=app1&deviceIds=D2&username=amq&password=amq&useAliases=true&rebirthDebounceDelay=2000&keepAliveTimeout=20&bdSeqNumPath=/myTmpDir/tahu";

        try (TahuDefaultEndpoint endpoint = TestSupport.resolveMandatoryEndpoint(context, uri, TahuDefaultEndpoint.class)) {

            assertThat(endpoint, is(notNullValue()));
            assertThat(endpoint,
                    allOf(hasProperty("groupId", is("Basic")),
                            hasProperty("edgeNode", is("EdgeNode")),
                            hasProperty("deviceIds", is("D2")),
                            hasProperty("deviceIdList", hasItems("D2")),
                            hasProperty("primaryHostId", is("app1")),
                            hasProperty("bdSeqNumPath", is("/myTmpDir/tahu")),
                            hasProperty("useAliases", is(true))));

            TahuConfiguration configuration = endpoint.getConfiguration();

            assertThat(configuration, is(notNullValue()));
            assertThat(configuration,
                    allOf(hasProperty("clientId", is("client1")),
                            hasProperty("checkClientIdLength", is(false)),
                            hasProperty("username", is("amq")),
                            hasProperty("password", is("amq")),
                            hasProperty("rebirthDebounceDelay", is(2000L)),
                            hasProperty("keepAliveTimeout", is(20))));
        }
    }

    @Test
    public void checkBasicEdgeNodeOptionsMultipleDevices() throws Exception {
        String uri
                = TahuConstants.EDGE_NODE_SCHEME + "://Basic/EdgeNode?clientId=client1&primaryHostId=app1&deviceIds=D2,D3,D4";

        try (TahuDefaultEndpoint endpoint = TestSupport.resolveMandatoryEndpoint(context, uri, TahuDefaultEndpoint.class)) {

            assertThat(endpoint, is(notNullValue()));
            assertThat(endpoint,
                    allOf(hasProperty("groupId", is("Basic")),
                            hasProperty("edgeNode", is("EdgeNode")),
                            hasProperty("deviceIds", is("D2,D3,D4")),
                            hasProperty("deviceIdList", hasItems("D2", "D3", "D4")),
                            hasProperty("primaryHostId", is("app1")),
                            hasProperty("useAliases", is(false))));
        }
    }

    @Test
    public void checkBasicDeviceOptions() throws Exception {
        String uri = TahuConstants.EDGE_NODE_SCHEME + "://Basic/EdgeNodeDevice/Device";

        try (TahuDefaultEndpoint endpoint = TestSupport.resolveMandatoryEndpoint(context, uri, TahuDefaultEndpoint.class)) {

            assertThat(endpoint, is(notNullValue()));
            assertThat(endpoint,
                    allOf(hasProperty("groupId", is("Basic")),
                            hasProperty("edgeNode", is("EdgeNodeDevice")),
                            hasProperty("deviceId", is("Device"))));

            TahuConfiguration configuration = endpoint.getConfiguration();

            assertThat(configuration, is(notNullValue()));
        }
    }

    @Test
    public void checkBasicHostAppOptions() throws Exception {
        String uri = TahuConstants.HOST_APP_SCHEME
                     + ":BasicHostApp?clientId=client1&username=amq&password=amq&rebirthDebounceDelay=2000&keepAliveTimeout=20";

        try (TahuDefaultEndpoint endpoint = TestSupport.resolveMandatoryEndpoint(context, uri, TahuDefaultEndpoint.class)) {

            assertThat(endpoint, is(notNullValue()));
            assertThat(endpoint, hasProperty("hostId", is("BasicHostApp")));

            TahuConfiguration configuration = endpoint.getConfiguration();

            assertThat(configuration, is(notNullValue()));
            assertThat(configuration,
                    allOf(hasProperty("clientId", is("client1")),
                            hasProperty("checkClientIdLength", is(false)),
                            hasProperty("username", is("amq")),
                            hasProperty("password", is("amq")),
                            hasProperty("rebirthDebounceDelay", is(2000L)),
                            hasProperty("keepAliveTimeout", is(20))));
        }
    }

    @Test
    public void checkEndpointUriServerDefs() {
        String uri
                = TahuConstants.EDGE_NODE_SCHEME
                  + "://EndpointUri/ServerDefs?servers=serverName1:clientId1:tcp://localhost:1883,serverName2:clientId1:tcp://localhost:1884";

        TahuDefaultEndpoint endpoint = TestSupport.resolveMandatoryEndpoint(context, uri, TahuDefaultEndpoint.class);

        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, allOf(hasProperty("groupId", is("EndpointUri")),
                hasProperty("edgeNode", is("ServerDefs"))));

        TahuConfiguration configuration = endpoint.getConfiguration();

        assertThat(configuration, is(notNullValue()));

        List<MqttServerDefinition> serverDefs = configuration.getServerDefinitionList();
        assertThat(serverDefs, hasSize(2));

        MqttServerDefinition serverDef = serverDefs.get(0);
        assertThat(serverDef.getMqttServerName(), hasProperty("mqttServerName", is("serverName1")));
        assertThat(serverDef.getMqttServerUrl(), hasProperty("mqttServerUrl", is("tcp://localhost:1883")));

        serverDef = serverDefs.get(1);
        assertThat(serverDef.getMqttServerName(), hasProperty("mqttServerName", is("serverName2")));
        assertThat(serverDef.getMqttServerUrl(), hasProperty("mqttServerUrl", is("tcp://localhost:1884")));

        assertThat(serverDefs,
                hasItems(allOf(hasProperty("mqttClientId",
                        hasProperty("mqttClientId", is("clientId1"))),
                        hasProperty("username", is(nullValue())),
                        hasProperty("password", is(nullValue())),
                        hasProperty("keepAliveTimeout",
                                is(configuration.getKeepAliveTimeout())),
                        hasProperty("ndeathTopic", is(nullValue())))));
    }

    @Test
    public void checkEndpointUriServerDefsSharedClientId() {
        String uri
                = TahuConstants.EDGE_NODE_SCHEME
                  + "://EndpointUri/ServerDefsSharedClientId?clientId=clientId2&username=user1&password=mysecretpassw0rd&keepAliveTimeout=45&servers=serverName1:tcp://localhost:1883,serverName2:tcp://localhost:1884";

        TahuDefaultEndpoint endpoint = TestSupport.resolveMandatoryEndpoint(context, uri, TahuDefaultEndpoint.class);

        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint,
                allOf(hasProperty("groupId", is("EndpointUri")),
                        hasProperty("edgeNode", is("ServerDefsSharedClientId"))));

        TahuConfiguration configuration = endpoint.getConfiguration();

        assertThat(configuration, is(notNullValue()));
        assertThat(configuration,
                allOf(hasProperty("clientId", is("clientId2")), hasProperty("checkClientIdLength", is(false)),
                        hasProperty("username", is("user1")),
                        hasProperty("password", is("mysecretpassw0rd")),
                        hasProperty("keepAliveTimeout", is(45))));

        List<MqttServerDefinition> serverDefs = configuration.getServerDefinitionList();
        assertThat(serverDefs, hasSize(2));

        MqttServerDefinition serverDef = serverDefs.get(0);
        assertThat(serverDef.getMqttServerName(), hasProperty("mqttServerName", is("serverName1")));
        assertThat(serverDef.getMqttServerUrl(), hasProperty("mqttServerUrl", is("tcp://localhost:1883")));

        serverDef = serverDefs.get(1);
        assertThat(serverDef.getMqttServerName(), hasProperty("mqttServerName", is("serverName2")));
        assertThat(serverDef.getMqttServerUrl(), hasProperty("mqttServerUrl", is("tcp://localhost:1884")));

        assertThat(serverDefs,
                hasItems(allOf(hasProperty("mqttClientId",
                        hasProperty("mqttClientId", is("clientId2"))),
                        hasProperty("username", is("user1")),
                        hasProperty("password", is("mysecretpassw0rd")),
                        hasProperty("keepAliveTimeout", is(45)),
                        hasProperty("ndeathTopic", is(nullValue())))));
    }

    @Test
    public void checkEndpointUriServerDefsNoClientId() {
        String uri
                = TahuConstants.EDGE_NODE_SCHEME
                  + "://EndpointUri/ServerDefsNoClientId?servers=serverName1:tcp://localhost:1883,serverName2:tcp://localhost:1884";

        TahuDefaultEndpoint endpoint = TestSupport.resolveMandatoryEndpoint(context, uri, TahuDefaultEndpoint.class);

        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint,
                allOf(hasProperty("groupId", is("EndpointUri")),
                        hasProperty("edgeNode", is("ServerDefsNoClientId"))));

        TahuConfiguration configuration = endpoint.getConfiguration();

        assertThat(configuration, is(notNullValue()));

        List<MqttServerDefinition> serverDefs = configuration.getServerDefinitionList();
        assertThat(serverDefs, hasSize(2));

        MqttServerDefinition serverDef = serverDefs.get(0);
        assertThat(serverDef.getMqttServerName(), hasProperty("mqttServerName", is("serverName1")));
        assertThat(serverDef.getMqttServerUrl(), hasProperty("mqttServerUrl", is("tcp://localhost:1883")));

        serverDef = serverDefs.get(1);
        assertThat(serverDef.getMqttServerName(), hasProperty("mqttServerName", is("serverName2")));
        assertThat(serverDef.getMqttServerUrl(), hasProperty("mqttServerUrl", is("tcp://localhost:1884")));

        assertThat(serverDefs,
                hasItems(allOf(hasProperty("mqttClientId",
                        hasProperty("mqttClientId", startsWith("Camel"))),
                        hasProperty("username", is(nullValue())),
                        hasProperty("password", is(nullValue())),
                        hasProperty("keepAliveTimeout",
                                is(configuration.getKeepAliveTimeout())),
                        hasProperty("ndeathTopic", is(nullValue())))));
    }
}
