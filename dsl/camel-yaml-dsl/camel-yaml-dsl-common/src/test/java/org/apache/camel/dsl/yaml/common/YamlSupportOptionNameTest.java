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
package org.apache.camel.dsl.yaml.common;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27362: only the option name of an endpoint parameter is turned into camelCase, not the part after a multiValue
 * prefix.
 */
class YamlSupportOptionNameTest {

    @Test
    void optionNameInDashFormIsCamelCase() {
        assertThat(YamlSupport.optionName("bridge-endpoint", Set.of("properties."))).isEqualTo("bridgeEndpoint");
        assertThat(YamlSupport.optionName("bridgeEndpoint", Set.of("properties."))).isEqualTo("bridgeEndpoint");
    }

    @Test
    void keyAfterMultiValuePrefixIsKept() {
        Set<String> prefixes = Set.of("properties.");
        assertThat(YamlSupport.optionName("properties.ws-security.username", prefixes))
                .isEqualTo("properties.ws-security.username");
        assertThat(YamlSupport.optionName("properties.security.callback-handler", prefixes))
                .isEqualTo("properties.security.callback-handler");
    }

    @Test
    void multiValuePrefixInDashForm() {
        assertThat(YamlSupport.optionName("ftp-client.data-timeout", List.of("ftpClient.")))
                .isEqualTo("ftpClient.data-timeout");
    }

    @Test
    void multiValuePrefixWithDash() {
        // servicenow has the prefixes request-model. and response-model.
        assertThat(YamlSupport.optionName("request-model.my-table", Set.of("request-model.", "response-model.")))
                .isEqualTo("request-model.my-table");
    }

    @Test
    void longestMultiValuePrefix() {
        // ftp has the nested prefixes ftpClient. and ftpClient.keyStore.
        Set<String> prefixes = Set.of("ftpClient.", "ftpClient.keyStore.");
        assertThat(YamlSupport.optionName("ftp-client.key-store.key-password", prefixes))
                .isEqualTo("ftpClient.keyStore.key-password");
        assertThat(YamlSupport.optionName("ftpClient.keyStore.type", prefixes)).isEqualTo("ftpClient.keyStore.type");
        assertThat(YamlSupport.optionName("ftp-client.so-timeout", prefixes)).isEqualTo("ftpClient.so-timeout");
    }

    @Test
    void withoutMultiValuePrefixTheKeyIsCamelCase() {
        // as before, such as when the component has no endpoint uri factory
        assertThat(YamlSupport.optionName("properties.ws-security.username", Set.of()))
                .isEqualTo("properties.wsSecurity.username");
        assertThat(YamlSupport.optionName("other.ws-security", Set.of("properties."))).isEqualTo("other.wsSecurity");
    }
}
