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

import org.apache.camel.CamelContext;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.model.ArtifactModel;

final class CatalogDependencyResolver {

    private final CamelContext camelContext;
    private final CamelCatalog catalog = new DefaultCamelCatalog();

    CatalogDependencyResolver(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    MavenGav resolve(String dependency) {
        MavenGav gav = MavenGav.parseGav(dependency, camelContext.getVersion());
        if (isCamelShorthand(dependency)) {
            ArtifactModel<?> model = catalog.modelFromMavenGAV(gav.getGroupId(), gav.getArtifactId(), null);
            if (model != null) {
                gav.setGroupId(model.getGroupId());
                gav.setArtifactId(model.getArtifactId());
                gav.setVersion(model.getVersion());
            }
        }
        return gav;
    }

    static boolean isCamelShorthand(String dependency) {
        return dependency.startsWith("camel:")
                || (dependency.startsWith("camel-") && !(dependency.contains(":") || dependency.contains("/")));
    }
}
