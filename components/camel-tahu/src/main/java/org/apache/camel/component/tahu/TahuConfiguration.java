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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.eclipse.tahu.mqtt.MqttClientId;
import org.eclipse.tahu.mqtt.MqttServerName;
import org.eclipse.tahu.mqtt.MqttServerUrl;

@UriParams
public class TahuConfiguration implements Cloneable {

    private static final Pattern SERVER_DEF_PATTERN = Pattern
            .compile("([^:]+):(?:(?!tcp|ssl)([^:]+):)?((?:tcp|ssl):(?://)?[\\p{Alnum}.-]+(?::\\d+)?),?");

    @UriParam(label = "common",
              description = "The MQTT servers, as a comma-separated list of definitions with the syntax"
                            + " name:[clientId:](tcp|ssl)://host[:port], for example Mqtt:tcp://localhost:1883. The clientId of a"
                            + " definition replaces the clientId option for that server. A tahu-edge Device endpoint does not"
                            + " use it, as its Edge Node endpoint connects to the servers.")
    @Metadata(applicableFor = { TahuConstants.EDGE_NODE_SCHEME, TahuConstants.HOST_APP_SCHEME }, required = true)
    private String servers;

    @UriParam(label = "common",
              description = "The MQTT client ID for each server whose definition in the servers option has no client ID. If"
                            + " neither is set, a random client ID that starts with Camel is generated for the server.")
    @Metadata(applicableFor = { TahuConstants.EDGE_NODE_SCHEME, TahuConstants.HOST_APP_SCHEME }, required = true)
    private String clientId;

    @UriParam(label = "common", defaultValue = "false",
              description = "Whether to fail when an MQTT client ID is longer than 23 characters, the limit of MQTT 3.1.1. A"
                            + " generated client ID is always shorter.")
    @Metadata(applicableFor = { TahuConstants.EDGE_NODE_SCHEME, TahuConstants.HOST_APP_SCHEME })
    private boolean checkClientIdLength = false;

    @UriParam(label = "security", security = "secret")
    @Metadata(applicableFor = { TahuConstants.EDGE_NODE_SCHEME, TahuConstants.HOST_APP_SCHEME })
    private String username;

    @UriParam(label = "security", security = "secret")
    @Metadata(applicableFor = { TahuConstants.EDGE_NODE_SCHEME, TahuConstants.HOST_APP_SCHEME })
    private String password;

    @UriParam(label = "common", defaultValue = "5000",
              description = "The time in milliseconds after a birth or rebirth during which the Edge Node ignores further"
                            + " rebirth requests. Only an Edge Node endpoint of tahu-edge uses it, tahu-host does not.")
    @Metadata(applicableFor = { TahuConstants.EDGE_NODE_SCHEME, TahuConstants.HOST_APP_SCHEME })
    private long rebirthDebounceDelay = 5000L;

    @UriParam(label = "common", defaultValue = "30")
    @Metadata(applicableFor = { TahuConstants.EDGE_NODE_SCHEME, TahuConstants.HOST_APP_SCHEME })
    private int keepAliveTimeout = 30;

    @UriParam(label = "security")
    @Metadata(applicableFor = { TahuConstants.EDGE_NODE_SCHEME, TahuConstants.HOST_APP_SCHEME })
    private SSLContextParameters sslContextParameters;

    public String getServers() {
        return servers;
    }

    public void setServers(String servers) {
        this.servers = servers;
    }

    public List<MqttServerDefinition> getServerDefinitionList() {
        List<MqttServerDefinition> serverDefinitionList;
        if (ObjectHelper.isEmpty(servers)) {
            serverDefinitionList = List.of();
        } else if (!SERVER_DEF_PATTERN.matcher(servers).find()) {
            throw new RuntimeCamelException("Server definition list has invalid syntax: " + servers);
        } else {
            Matcher serverDefMatcher = SERVER_DEF_PATTERN.matcher(servers);
            serverDefinitionList = serverDefMatcher.results().map(matchResult -> {

                // MatchResult does not support named groups
                String serverName = matchResult.group(1);
                String clientId = matchResult.group(2);
                String serverUrl = matchResult.group(3);

                return parseFromUrlString(serverName, clientId, serverUrl);
            }).toList();
        }
        return serverDefinitionList;
    }

    private MqttServerDefinition parseFromUrlString(
            String serverName, String clientId, String serverUrl) {
        try {
            MqttServerName mqttServerName = new MqttServerName(ObjectHelper.notNullOrEmpty(serverName, "serverName"));

            clientId = Stream.of(clientId, this.clientId).filter(ObjectHelper::isNotEmpty).findFirst()
                    .orElse(MqttClientId.generate("Camel"));
            MqttClientId mqttClientId = new MqttClientId(clientId, checkClientIdLength);

            return new MqttServerDefinition(
                    mqttServerName, mqttClientId, new MqttServerUrl(ObjectHelper.notNullOrEmpty(serverUrl, "serverUrl")),
                    username, password, keepAliveTimeout, null);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public boolean isCheckClientIdLength() {
        return checkClientIdLength;
    }

    public void setCheckClientIdLength(boolean checkClientIdLength) {
        this.checkClientIdLength = checkClientIdLength;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Username for MQTT server authentication
     *
     * @param username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password for MQTT server authentication
     *
     * @param password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public long getRebirthDebounceDelay() {
        return rebirthDebounceDelay;
    }

    public void setRebirthDebounceDelay(long rebirthDebounceDelay) {
        this.rebirthDebounceDelay = rebirthDebounceDelay;
    }

    public int getKeepAliveTimeout() {
        return keepAliveTimeout;
    }

    /**
     * MQTT connection keep alive timeout, in seconds
     *
     * @param keepAliveTimeout
     */
    public void setKeepAliveTimeout(int keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * SSL configuration for MQTT server connections
     *
     * @param sslContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public TahuConfiguration copy() {
        try {
            return (TahuConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
