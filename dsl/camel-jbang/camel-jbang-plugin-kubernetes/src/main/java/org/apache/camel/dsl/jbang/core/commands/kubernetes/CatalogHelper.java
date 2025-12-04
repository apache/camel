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

package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;

public class CatalogHelper {

    private CatalogHelper() {
        // prevent instantiation of utility class
    }

    public static CamelCatalog loadCatalog(RuntimeType runtime, String runtimeVersion, boolean download)
            throws Exception {
        return loadCatalog(runtime, runtimeVersion, "", null, download);
    }

    public static CamelCatalog loadCatalog(
            RuntimeType runtime, String runtimeVersion, String repos, String quarkusGroupId, boolean download)
            throws Exception {
        switch (runtime) {
            case springBoot:
                return CatalogLoader.loadSpringBootCatalog(repos, runtimeVersion, download);
            case quarkus:
                return CatalogLoader.loadQuarkusCatalog(repos, runtimeVersion, quarkusGroupId, download);
            case main:
                return CatalogLoader.loadCatalog(repos, runtimeVersion, download);
            default:
                throw new IllegalArgumentException("Unsupported runtime: " + runtime);
        }
    }
}
