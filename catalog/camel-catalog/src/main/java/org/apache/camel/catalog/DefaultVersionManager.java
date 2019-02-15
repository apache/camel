/**
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
package org.apache.camel.catalog;

import java.io.InputStream;

public class DefaultVersionManager implements VersionManager {

    private final CamelCatalog camelCatalog;

    public DefaultVersionManager(CamelCatalog camelCatalog) {
        this.camelCatalog = camelCatalog;
    }

    @Override
    public String getLoadedVersion() {
        return camelCatalog.getCatalogVersion();
    }

    @Override
    public boolean loadVersion(String version) {
        return getLoadedVersion().equals(version);
    }

    @Override
    public String getRuntimeProviderLoadedVersion() {
        // not supported
        return null;
    }

    @Override
    public boolean loadRuntimeProviderVersion(String groupId, String artifactId, String version) {
        // not supported
        return false;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return DefaultCamelCatalog.class.getClassLoader().getResourceAsStream(name);
    }
}
