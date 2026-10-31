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
package org.apache.camel.dsl.jbang.run;

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.main.download.DownloadListener;
import org.apache.camel.util.ObjectHelper;

/**
 * Writes the dependencies, the extra repositories and the Kamelets that a run loads to the run settings.
 *
 * @since 4.24
 */
public class RunDownloadListener implements DownloadListener {

    private final RunSettings settings;
    private final Set<String> downloaded = new HashSet<>();
    private final Set<String> repos = new HashSet<>();
    private final Set<String> kamelets = new HashSet<>();

    public RunDownloadListener(RunSettings settings) {
        this.settings = settings;
    }

    @Override
    public void onDownloadDependency(String groupId, String artifactId, String version) {
        String line = "mvn:" + groupId + ":" + artifactId;
        if (ObjectHelper.isNotEmpty(version)) {
            line += ":" + version;
        }
        if (!downloaded.contains(line)) {
            settings.write("dependency", line);
            downloaded.add(line);
        }
    }

    @Override
    public void onAlreadyDownloadedDependency(String groupId, String artifactId, String version) {
        // we want to register everything
        onDownloadDependency(groupId, artifactId, version);
    }

    @Override
    public void onExtraRepository(String repo) {
        if (!repos.contains(repo)) {
            settings.write("repository", repo);
            repos.add(repo);
        }
    }

    @Override
    public void onLoadingKamelet(String name) {
        if (!kamelets.contains(name)) {
            settings.write("kamelet", name);
            kamelets.add(name);
        }
    }
}
