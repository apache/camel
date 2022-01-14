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
package org.apache.camel.component.knative;

import java.util.Map;

import org.apache.camel.component.knative.spi.Knative;
import org.apache.camel.component.knative.spi.KnativeEnvironment;
import org.apache.camel.component.knative.spi.KnativeResource;

/**
 * Support for knative environments.
 */
public final class KnativeEnvironmentSupport {

    private KnativeEnvironmentSupport() {
    }

    public static KnativeResource endpoint(Knative.EndpointKind endpointKind, String name, String url) {
        return KnativeEnvironment.serviceBuilder(Knative.Type.endpoint, name)
                .withUrl(url)
                .withEndpointKind(endpointKind)
                .build();
    }

    public static KnativeResource endpoint(
            Knative.EndpointKind endpointKind, String name, String url, Map<String, String> metadata) {
        return KnativeEnvironment.serviceBuilder(Knative.Type.endpoint, name)
                .withUrl(url)
                .withMeta(metadata)
                .withEndpointKind(endpointKind)
                .build();
    }

    public static KnativeResource sourceEndpoint(String name, Map<String, String> metadata) {
        return KnativeEnvironment.serviceBuilder(Knative.Type.endpoint, name)
                .withMeta(metadata)
                .withEndpointKind(Knative.EndpointKind.source)
                .build();
    }

    public static KnativeResource channel(Knative.EndpointKind endpointKind, String name, String url) {
        return KnativeEnvironment.serviceBuilder(Knative.Type.channel, name)
                .withUrl(url)
                .withEndpointKind(endpointKind)
                .build();
    }

    public static KnativeResource channel(
            Knative.EndpointKind endpointKind, String name, String url, Map<String, String> metadata) {
        return KnativeEnvironment.serviceBuilder(Knative.Type.channel, name)
                .withUrl(url)
                .withMeta(metadata)
                .withEndpointKind(endpointKind)
                .build();
    }

    public static KnativeResource sourceChannel(String name, Map<String, String> metadata) {
        return KnativeEnvironment.serviceBuilder(Knative.Type.channel, name)
                .withMeta(metadata)
                .withEndpointKind(Knative.EndpointKind.source)
                .build();
    }

    public static KnativeResource event(Knative.EndpointKind endpointKind, String name, String url) {
        return KnativeEnvironment.serviceBuilder(Knative.Type.event, name)
                .withUrl(url)
                .withEndpointKind(endpointKind)
                .build();
    }

    public static KnativeResource sourceEvent(String name) {
        return KnativeEnvironment.serviceBuilder(Knative.Type.event, name)
                .withEndpointKind(Knative.EndpointKind.source)
                .build();
    }

    public static KnativeResource sourceEvent(String name, Map<String, String> metadata) {
        return KnativeEnvironment.serviceBuilder(Knative.Type.event, name)
                .withMeta(metadata)
                .withEndpointKind(Knative.EndpointKind.source)
                .build();
    }

    public static KnativeResource event(
            Knative.EndpointKind endpointKind, String name, String url, Map<String, String> metadata) {
        return KnativeEnvironment.serviceBuilder(Knative.Type.event, name)
                .withUrl(url)
                .withMeta(metadata)
                .withEndpointKind(endpointKind)
                .build();
    }
}
