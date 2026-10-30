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
package org.apache.camel.dsl.jbang.core.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.main.download.DownloadException;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifactDescriptor;

/**
 * A downloader with the releases of a fixture plugin, without Maven.
 */
public class FakePluginDownloader extends MavenDependencyDownloader {

    public final List<String> versions = new ArrayList<>();
    public final Map<String, MavenArtifactDescriptor> descriptors = new HashMap<>();
    public final List<String> descriptorsRead = new ArrayList<>();
    public boolean listingDown;
    public boolean descriptorsDown;
    public Boolean listedFresh;

    public FakePluginDownloader release(String version, String camelJBangCoreVersion, String camelVersions) {
        versions.add(version);
        descriptors.put(version, PluginCompatibilityTest.descriptor(camelJBangCoreVersion, camelVersions));
        return this;
    }

    @Override
    public List<String> resolveAvailableVersions(String groupId, String artifactId, boolean fresh) {
        listedFresh = fresh;
        if (listingDown) {
            throw new DownloadException("Cannot resolve available versions of " + groupId + ":" + artifactId);
        }
        return new ArrayList<>(versions);
    }

    @Override
    public MavenArtifactDescriptor resolveArtifactDescriptor(
            String groupId, String artifactId, String version, String repos) {
        descriptorsRead.add(version);
        MavenArtifactDescriptor answer = descriptors.get(version);
        if (descriptorsDown || answer == null) {
            throw new DownloadException("cannot download the POM of " + version);
        }
        return answer;
    }
}
