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

import java.util.List;
import java.util.Map;

public final class IngressBuilder {
    private Map<String, String> annotations;
    private Boolean auto;
    private Boolean enabled;
    private String host;
    private String path;
    private Ingress.PathType pathType;
    private List<String> tlsHosts;
    private String tlsSecretName;
    private String ingressClass;

    private IngressBuilder() {
    }

    public static IngressBuilder ingress() {
        return new IngressBuilder();
    }

    public IngressBuilder withAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
        return this;
    }

    public IngressBuilder withAuto(Boolean auto) {
        this.auto = auto;
        return this;
    }

    public IngressBuilder withEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public IngressBuilder withHost(String host) {
        this.host = host;
        return this;
    }

    public IngressBuilder withPath(String path) {
        this.path = path;
        return this;
    }

    public IngressBuilder withPathType(Ingress.PathType pathType) {
        this.pathType = pathType;
        return this;
    }

    public IngressBuilder withTlsHosts(List<String> tlsHosts) {
        this.tlsHosts = tlsHosts;
        return this;
    }

    public IngressBuilder withTlsSecretName(String tlsSecretName) {
        this.tlsSecretName = tlsSecretName;
        return this;
    }

    public IngressBuilder withIngressClass(String ingressClass) {
        this.ingressClass = ingressClass;
        return this;
    }

    public Ingress build() {
        Ingress ingress = new Ingress();
        ingress.setAnnotations(annotations);
        ingress.setAuto(auto);
        ingress.setEnabled(enabled);
        ingress.setHost(host);
        ingress.setPath(path);
        ingress.setPathType(pathType);
        ingress.setTlsHosts(tlsHosts);
        ingress.setTlsSecretName(tlsSecretName);
        ingress.setIngressClass(ingressClass);
        return ingress;
    }
}
