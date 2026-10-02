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
package org.apache.camel.main.download;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.model.ArtifactModel;

final class CatalogDependencyResolver {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();
    private static final ConcurrentMap<String, Optional<Coordinates>> COORDINATES = new ConcurrentHashMap<>();

    private CatalogDependencyResolver() {
    }

    static MavenGav resolve(String dependency, String defaultVersion) {
        MavenGav gav = MavenGav.parseGav(dependency, defaultVersion);
        if (isCamelShorthand(dependency)) {
            COORDINATES.computeIfAbsent(gav.getArtifactId(), CatalogDependencyResolver::lookup)
                    .ifPresent(coordinates -> {
                        gav.setGroupId(coordinates.groupId());
                        gav.setArtifactId(coordinates.artifactId());
                        gav.setVersion(coordinates.version());
                    });
        }
        return gav;
    }

    private static Optional<Coordinates> lookup(String artifactId) {
        ArtifactModel<?> model = CATALOG.modelFromMavenGAV("org.apache.camel", artifactId, null);
        return Optional.ofNullable(model)
                .map(m -> new Coordinates(m.getGroupId(), m.getArtifactId(), m.getVersion()));
    }

    static boolean isCamelShorthand(String dependency) {
        return dependency.startsWith("camel:")
                || (dependency.startsWith("camel-") && !(dependency.contains(":") || dependency.contains("/")));
    }

    private record Coordinates(String groupId, String artifactId, String version) {
    }
}
