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

public final class MountBuilder {
    private List<String> configs;
    private List<String> emptyDirs;
    private Boolean enabled;
    private Boolean hotReload;
    private List<String> resources;
    private Boolean scanKameletsImplicitLabelSecrets;
    private List<String> volumes;

    private MountBuilder() {
    }

    public static MountBuilder mount() {
        return new MountBuilder();
    }

    public MountBuilder withConfigs(List<String> configs) {
        this.configs = configs;
        return this;
    }

    public MountBuilder withEmptyDirs(List<String> emptyDirs) {
        this.emptyDirs = emptyDirs;
        return this;
    }

    public MountBuilder withEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public MountBuilder withHotReload(Boolean hotReload) {
        this.hotReload = hotReload;
        return this;
    }

    public MountBuilder withResources(List<String> resources) {
        this.resources = resources;
        return this;
    }

    public MountBuilder withScanKameletsImplicitLabelSecrets(Boolean scanKameletsImplicitLabelSecrets) {
        this.scanKameletsImplicitLabelSecrets = scanKameletsImplicitLabelSecrets;
        return this;
    }

    public MountBuilder withVolumes(List<String> volumes) {
        this.volumes = volumes;
        return this;
    }

    public Mount build() {
        Mount mount = new Mount();
        mount.setConfigs(configs);
        mount.setEmptyDirs(emptyDirs);
        mount.setEnabled(enabled);
        mount.setHotReload(hotReload);
        mount.setResources(resources);
        mount.setScanKameletsImplicitLabelSecrets(scanKameletsImplicitLabelSecrets);
        mount.setVolumes(volumes);
        return mount;
    }
}
