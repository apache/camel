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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.main.download.DownloadException;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifactDescriptor;

/**
 * A downloader with the releases of a fixture plugin, without Maven.
 */
public class FakePluginDownloader extends MavenDependencyDownloader {

    public final Map<String, List<String>> versionsPerRepo = new HashMap<>();
    public final Map<String, MavenArtifactDescriptor> descriptors = new HashMap<>();
    public final Set<String> down = new HashSet<>();
    public final List<String> descriptorsRead = new ArrayList<>();
    public boolean descriptorsDown;

    public FakePluginDownloader release(String version, String camelJBangCoreVersion, String camelVersions) {
        return releaseIn(null, version, camelJBangCoreVersion, camelVersions);
    }

    public FakePluginDownloader releaseIn(String repo, String version, String camelJBangCoreVersion, String camelVersions) {
        versionsPerRepo.computeIfAbsent(repo, r -> new ArrayList<>()).add(version);
        descriptors.put(version, PluginCompatibilityTest.descriptor(camelJBangCoreVersion, camelVersions));
        return this;
    }

    @Override
    public List<String[]> resolveAvailableVersions(
            String groupId, String artifactId, String minimumVersion, String repo) {
        if (down.contains(repo)) {
            throw new DownloadException((repo != null ? repo : "Maven Central") + " cannot be reached");
        }
        List<String[]> answer = new ArrayList<>();
        for (String v : versionsPerRepo.getOrDefault(repo, List.of())) {
            answer.add(new String[] { v, null });
        }
        return answer;
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
