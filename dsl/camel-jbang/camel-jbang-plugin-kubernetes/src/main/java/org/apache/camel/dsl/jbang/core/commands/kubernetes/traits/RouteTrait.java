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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.IntOrStringBuilder;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.TLSConfigBuilder;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.ClusterType;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Container;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Route;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.ContainerTrait.DEFAULT_CONTAINER_PORT_NAME;

public class RouteTrait extends BaseTrait {

    public static final int RouteTrait = 2200;

    public RouteTrait() {
        super("route", RouteTrait);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        if (context.getRoute().isPresent()) {
            return false;
        }

        // must be explicitly enabled
        if (traitConfig.getRoute() == null || !Optional.ofNullable(traitConfig.getRoute().getEnabled()).orElse(false)) {
            return false;
        }

        // configured service
        return context.getService().isPresent();
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        Route routeTrait = Optional.ofNullable(traitConfig.getRoute()).orElseGet(Route::new);
        Container containerTrait = Optional.ofNullable(traitConfig.getContainer()).orElseGet(Container::new);

        RouteBuilder routeBuilder = new RouteBuilder();
        routeBuilder.withNewMetadata()
                .withName(context.getName())
                .endMetadata();
        if (routeTrait.getAnnotations() != null) {
            routeBuilder.editMetadata().withAnnotations(routeTrait.getAnnotations()).endMetadata();
        }

        IntOrString servicePortName = new IntOrStringBuilder()
                .withValue(Optional.ofNullable(containerTrait.getServicePortName()).orElse(DEFAULT_CONTAINER_PORT_NAME))
                .build();
        routeBuilder
                .withNewSpec()
                .withNewPort()
                .withTargetPort(servicePortName)
                .endPort()
                .withNewTo()
                .withKind("Service")
                .withName(context.getName())
                .endTo()
                .endSpec();

        if (routeTrait.getHost() != null) {
            routeBuilder.editSpec().withHost(routeTrait.getHost()).endSpec();
        }

        TLSConfigBuilder tlsConfigBuilder = new TLSConfigBuilder();
        if (routeTrait.getTlsTermination() != null) {
            tlsConfigBuilder.withTermination(routeTrait.getTlsTermination().getValue());
        }
        if (routeTrait.getTlsCertificate() != null) {
            tlsConfigBuilder.withCertificate(getContent(routeTrait.getTlsCertificate()));
        }
        if (routeTrait.getTlsKey() != null) {
            tlsConfigBuilder.withKey(getContent(routeTrait.getTlsKey()));
        }
        if (routeTrait.getTlsCACertificate() != null) {
            tlsConfigBuilder.withCaCertificate(getContent(routeTrait.getTlsCACertificate()));
        }
        if (routeTrait.getTlsDestinationCACertificate() != null) {
            tlsConfigBuilder.withDestinationCACertificate(getContent(routeTrait.getTlsDestinationCACertificate()));
        }

        if (routeTrait.getTlsInsecureEdgeTerminationPolicy() != null) {
            tlsConfigBuilder.withInsecureEdgeTerminationPolicy(routeTrait.getTlsInsecureEdgeTerminationPolicy().getValue());
        }

        routeBuilder.editSpec().withTls(tlsConfigBuilder.build()).endSpec();
        context.add(routeBuilder);

    }

    @Override
    public boolean accept(ClusterType clusterType) {
        return ClusterType.OPENSHIFT == clusterType;
    }

    private String getContent(String value) {
        if (value.startsWith("file:")) {
            String filePath = StringHelper.after(value, ":");
            final File file = new File(filePath);
            if (!file.exists()) {
                throw new RuntimeException(filePath + " does not exist");
            }
            if (file.isDirectory()) {
                throw new RuntimeException(filePath + " is not a file");
            }
            try (InputStream is = new FileInputStream(file)) {
                return IOHelper.loadText(is);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return value;
        }
    }

}
