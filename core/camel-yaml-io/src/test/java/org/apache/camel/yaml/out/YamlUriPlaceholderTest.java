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
package org.apache.camel.yaml.out;

import java.lang.reflect.Proxy;
import java.util.Map;

import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YamlUriPlaceholderTest {

    @Test
    void theQuestionMarkOfAnOptionalPlaceholderIsNotTheQuery() {
        // the wttrin-source Kamelet: split at the ? of {{?wttrLocation}} it became httpUri "wttr.in/{{"
        assertThat(YamlModelWriterSupport.questionMarkInPlaceholder(
                "https://wttr.in/{{?wttrLocation}}?format=j1&lang={{?wttrLanguage}}")).isTrue();
        assertThat(YamlModelWriterSupport.questionMarkInPlaceholder(
                "https://wttr.in/{{location}}?format=j1&lang={{?wttrLanguage}}")).isFalse();
        assertThat(YamlModelWriterSupport.questionMarkInPlaceholder("timer:tick?period={{?period}}")).isFalse();
        assertThat(YamlModelWriterSupport.questionMarkInPlaceholder("log:{{loggerName}}")).isFalse();
    }

    @Test
    void aPathTheCatalogCannotWriteBackIsKeptAsWritten() {
        // the camel-kamelets azure-storage-blob-changefeed-source: the one path part went to containerName
        RuntimeCamelCatalog wrong = catalog("azure-storage-blob:/{{accountName}}");
        assertThat(YamlModelWriterSupport.rebuildsThePath(wrong, "azure-storage-blob:{{accountName}}",
                Map.of("containerName", "{{accountName}}"))).isFalse();
        RuntimeCamelCatalog right = catalog("azure-storage-blob:{{accountName}}/{{containerName}}?operation=x");
        assertThat(YamlModelWriterSupport.rebuildsThePath(right,
                "azure-storage-blob:{{accountName}}/{{containerName}}?operation=x",
                Map.of("accountName", "{{accountName}}", "containerName", "{{containerName}}", "operation", "x")))
                .isTrue();
        // a required option missing: the catalog cannot build it
        assertThat(YamlModelWriterSupport.rebuildsThePath(catalog(null), "pulsar:a/b/c/d", Map.of())).isFalse();
    }

    @Test
    void fewerPathPartsThanTheSyntax() {
        RuntimeCamelCatalog catalog = (RuntimeCamelCatalog) Proxy.newProxyInstance(
                RuntimeCamelCatalog.class.getClassLoader(), new Class<?>[] { RuntimeCamelCatalog.class },
                (proxy, method, args) -> {
                    if ("componentJSonSchema".equals(method.getName())) {
                        String syntax = Map.of("azure-storage-blob", "azure-storage-blob:accountName/containerName",
                                "timer", "timer:timerName").get((String) args[0]);
                        return syntax == null ? null : "{\"component\": {\"syntax\": \"" + syntax + "\"}}";
                    }
                    return null;
                });
        // the round trip writes containerName={{accountName}} back as the same text, so it needs this check too
        assertThat(YamlModelWriterSupport.fewerPathPartsThanSyntax(catalog, "azure-storage-blob:{{accountName}}"))
                .isTrue();
        assertThat(YamlModelWriterSupport.fewerPathPartsThanSyntax(catalog, "azure-storage-blob:a/b?x=y")).isFalse();
        assertThat(YamlModelWriterSupport.fewerPathPartsThanSyntax(catalog, "timer:tick?period=5")).isFalse();
        assertThat(YamlModelWriterSupport.fewerPathPartsThanSyntax(catalog, "unknown:x")).isFalse();
    }

    /** A catalog whose asEndpointUri answers the given uri, or fails when it is null. */
    private static RuntimeCamelCatalog catalog(String rebuilt) {
        return (RuntimeCamelCatalog) Proxy.newProxyInstance(RuntimeCamelCatalog.class.getClassLoader(),
                new Class<?>[] { RuntimeCamelCatalog.class }, (proxy, method, args) -> {
                    if ("asEndpointUri".equals(method.getName())) {
                        if (rebuilt == null) {
                            throw new IllegalArgumentException("Option topic is required");
                        }
                        return rebuilt;
                    }
                    return null;
                });
    }
}
