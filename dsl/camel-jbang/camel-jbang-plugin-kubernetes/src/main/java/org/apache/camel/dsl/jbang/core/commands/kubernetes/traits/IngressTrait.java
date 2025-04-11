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
package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.ClusterType;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Container;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Ingress;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;

import static org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.ContainerTrait.DEFAULT_CONTAINER_PORT_NAME;

public class IngressTrait extends BaseTrait {

    public static final int IngressTrait = 2400;

    public static final String DEFAULT_INGRESS_HOST = "";
    public static final String DEFAULT_INGRESS_PATH = "/";
    public static final String DEFAULT_INGRESS_CLASS_NAME = "nginx";
    public static final Ingress.PathType DEFAULT_INGRESS_PATH_TYPE = Ingress.PathType.PREFIX;

    public IngressTrait() {
        super("ingress", IngressTrait);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        if (context.getIngress().isPresent()) {
            return false;
        }

        // must be explicitly enabled
        if (traitConfig.getIngress() == null || !Optional.ofNullable(traitConfig.getIngress().getEnabled()).orElse(false)) {
            return false;
        }

        // configured service
        return context.getService().isPresent();
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        Ingress ingressTrait = Optional.ofNullable(traitConfig.getIngress()).orElseGet(Ingress::new);
        Container containerTrait = Optional.ofNullable(traitConfig.getContainer()).orElseGet(Container::new);

        IngressBuilder ingressBuilder = new IngressBuilder();
        ingressBuilder.withNewMetadata()
                .withName(context.getName())
                .endMetadata();
        if (ingressTrait.getAnnotations() != null) {
            ingressBuilder.editMetadata().withAnnotations(ingressTrait.getAnnotations()).endMetadata();
        }

        HTTPIngressPath path = new HTTPIngressPathBuilder()
                .withPath(Optional.ofNullable(ingressTrait.getPath()).orElse(DEFAULT_INGRESS_PATH))
                .withPathType(Optional.ofNullable(ingressTrait.getPathType()).orElse(DEFAULT_INGRESS_PATH_TYPE).getValue())
                .withNewBackend()
                .withNewService()
                .withName(context.getName())
                .withNewPort()
                .withName(Optional.ofNullable(containerTrait.getServicePortName()).orElse(DEFAULT_CONTAINER_PORT_NAME))
                .endPort()
                .endService()
                .endBackend()
                .build();

        IngressRule rule = new IngressRuleBuilder()
                .withHost(Optional.ofNullable(ingressTrait.getHost()).orElse(DEFAULT_INGRESS_HOST))
                .withNewHttp()
                .withPaths(path)
                .endHttp()
                .build();

        ingressBuilder
                .withNewSpec()
                .withIngressClassName(Optional.ofNullable(ingressTrait.getIngressClass()).orElse(DEFAULT_INGRESS_CLASS_NAME))
                .withRules(rule)
                .endSpec();

        if (ingressTrait.getTlsHosts() != null && ingressTrait.getTlsSecretName() != null) {
            IngressTLS tls = new IngressTLSBuilder()
                    .withHosts(ingressTrait.getTlsHosts())
                    .withSecretName(ingressTrait.getTlsSecretName())
                    .build();
            ingressBuilder.editSpec().withTls(tls).endSpec();
        }

        context.add(ingressBuilder);
    }

    @Override
    public boolean accept(ClusterType clusterType) {
        return ClusterType.OPENSHIFT != clusterType;
    }
}
