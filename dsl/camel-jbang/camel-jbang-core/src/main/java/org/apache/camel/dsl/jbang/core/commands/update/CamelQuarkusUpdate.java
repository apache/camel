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
package org.apache.camel.dsl.jbang.core.commands.update;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.main.download.MavenDependencyDownloader;

public final class CamelQuarkusUpdate implements Update {

    private List<String> commands = new ArrayList<>();
    private CamelUpdateMixin updateMixin;
    private MavenDependencyDownloader downloader;

    private final String QUARKUS_UPDATE_ARTIFACTID = "camel-quarkus-catalog";

    public CamelQuarkusUpdate(CamelUpdateMixin updateMixin, MavenDependencyDownloader downloader) {
        this.updateMixin = updateMixin;
        this.downloader = downloader;
    }

    /**
     * Quarkus updates are in the form 3.8, 3.15, 3.17... Downloads Camel Quarkus catalog for the given Camel version
     * and get the Quarkus stream version
     *
     * @return
     */
    public String getQuarkusStream() {
        // Assume that the quarkus updates are in the form 3.8, 3.15, 3.16...
        List<String[]> qVersions
                = downloader.resolveAvailableVersions("org.apache.camel.quarkus", QUARKUS_UPDATE_ARTIFACTID,
                        updateMixin.version,
                        updateMixin.repos);
        String streamVersion = null;
        for (String[] qVersion : qVersions) {
            if (qVersion[0].equals(updateMixin.version)) {
                streamVersion = qVersion[1].substring(0, qVersion[1].lastIndexOf('.'));
            }
        }

        return streamVersion;
    }

    @Override
    public String debug() {
        String result = "--no-transfer-progress";
        if (updateMixin.debug) {
            result = "-X";
        }

        return result;
    }

    @Override
    public String runMode() {
        String result = "-DrewriteFullRun";
        if (updateMixin.dryRun) {
            result = "-DrewriteDryRun";
        }

        return result;
    }

    @Override
    public List<String> command() throws CamelUpdateException {
        commands.add(mvnProgramCall());
        commands.add(debug());
        commands.add(String.format("%s:quarkus-maven-plugin:%s:update", updateMixin.quarkusMavenPluginGroupId,
                updateMixin.quarkusMavenPluginVersion));
        commands.add("-Dstream=" + getQuarkusStream());
        commands.add(runMode());

        return commands;
    }

    @Override
    public String getArtifactCoordinates() {
        return QUARKUS_UPDATE_ARTIFACTID;
    }
}
