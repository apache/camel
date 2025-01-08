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
package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model;

import java.util.Map;

public final class RouteBuilder {
    private Map<String, String> annotations;
    private Boolean enabled;
    private String host;
    private String tlsCACertificate;
    private String tlsCACertificateSecret;
    private String tlsCertificate;
    private String tlsCertificateSecret;
    private String tlsDestinationCACertificate;
    private String tlsDestinationCACertificateSecret;
    private Route.TlsInsecureEdgeTerminationPolicy tlsInsecureEdgeTerminationPolicy;
    private String tlsKey;
    private String tlsKeySecret;
    private Route.TlsTermination tlsTermination;

    private RouteBuilder() {
    }

    public static RouteBuilder route() {
        return new RouteBuilder();
    }

    public RouteBuilder withAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
        return this;
    }

    public RouteBuilder withEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public RouteBuilder withHost(String host) {
        this.host = host;
        return this;
    }

    public RouteBuilder withTlsCACertificate(String tlsCACertificate) {
        this.tlsCACertificate = tlsCACertificate;
        return this;
    }

    public RouteBuilder withTlsCACertificateSecret(String tlsCACertificateSecret) {
        this.tlsCACertificateSecret = tlsCACertificateSecret;
        return this;
    }

    public RouteBuilder withTlsCertificate(String tlsCertificate) {
        this.tlsCertificate = tlsCertificate;
        return this;
    }

    public RouteBuilder withTlsCertificateSecret(String tlsCertificateSecret) {
        this.tlsCertificateSecret = tlsCertificateSecret;
        return this;
    }

    public RouteBuilder withTlsDestinationCACertificate(String tlsDestinationCACertificate) {
        this.tlsDestinationCACertificate = tlsDestinationCACertificate;
        return this;
    }

    public RouteBuilder withTlsDestinationCACertificateSecret(String tlsDestinationCACertificateSecret) {
        this.tlsDestinationCACertificateSecret = tlsDestinationCACertificateSecret;
        return this;
    }

    public RouteBuilder withTlsInsecureEdgeTerminationPolicy(
            Route.TlsInsecureEdgeTerminationPolicy tlsInsecureEdgeTerminationPolicy) {
        this.tlsInsecureEdgeTerminationPolicy = tlsInsecureEdgeTerminationPolicy;
        return this;
    }

    public RouteBuilder withTlsKey(String tlsKey) {
        this.tlsKey = tlsKey;
        return this;
    }

    public RouteBuilder withTlsKeySecret(String tlsKeySecret) {
        this.tlsKeySecret = tlsKeySecret;
        return this;
    }

    public RouteBuilder withTlsTermination(Route.TlsTermination tlsTermination) {
        this.tlsTermination = tlsTermination;
        return this;
    }

    public Route build() {
        Route route = new Route();
        route.setAnnotations(annotations);
        route.setEnabled(enabled);
        route.setHost(host);
        route.setTlsCACertificate(tlsCACertificate);
        route.setTlsCACertificateSecret(tlsCACertificateSecret);
        route.setTlsCertificate(tlsCertificate);
        route.setTlsCertificateSecret(tlsCertificateSecret);
        route.setTlsDestinationCACertificate(tlsDestinationCACertificate);
        route.setTlsDestinationCACertificateSecret(tlsDestinationCACertificateSecret);
        route.setTlsInsecureEdgeTerminationPolicy(tlsInsecureEdgeTerminationPolicy);
        route.setTlsKey(tlsKey);
        route.setTlsKeySecret(tlsKeySecret);
        route.setTlsTermination(tlsTermination);
        return route;
    }
}
