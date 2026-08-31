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
package org.apache.camel.main.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.DownloadRecord;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "dependency-downloader", group = "camel-jbang", displayName = "Maven Dependency Downloader",
            description = "Displays information about dependencies downloaded at runtime")
public class DependencyDownloaderConsole extends AbstractDevConsole {

    public record Response(
            @Metadata(description = "The downloaded dependency class-path entries (only present when the application classloader manages downloads)") List<String> dependencies,
            @Metadata(description = "Whether downloading is disabled (only present when a downloader is active)") Boolean offline,
            @Metadata(description = "Whether fresh downloads are forced (only present when a downloader is active)") Boolean fresh,
            @Metadata(description = "Whether verbose logging is enabled (only present when a downloader is active)") Boolean verbose,
            @Metadata(description = "The extra Maven repositories (only present when a downloader is active)") String repos,
            @Metadata(description = "The downloaded artifacts (only present when a downloader is active)") List<DownloadRecord> downloads) {
    }

    public DependencyDownloaderConsole() {
        super("camel-jbang", "dependency-downloader", "Maven Dependency Downloader",
              "Displays information about dependencies downloaded at runtime");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        MavenDependencyDownloader downloader = getCamelContext().hasService(MavenDependencyDownloader.class);
        if (downloader != null) {
            sb.append("Offline: ").append(!downloader.isDownload());
            sb.append("\nFresh:   ").append(downloader.isFresh());
            sb.append("\nVerbose: ").append(downloader.isVerbose());
            if (downloader.getRepositories() != null) {
                sb.append("\nExtra Repositories: ").append(downloader.getRepositories());
            }
            sb.append("\n");
            sb.append("\nDownloads:");
            for (DownloadRecord r : downloader.downloadRecords()) {
                sb.append("\n    ").append(String.format("%s:%s:%s (took: %s) from: %s@%s",
                        r.groupId(), r.artifactId(), r.version(), TimeUtils.printDuration(r.elapsed(), true), r.repoId(),
                        r.repoUrl()));
            }
        }

        ClassLoader cl = getCamelContext().getApplicationContextClassLoader();
        if (cl instanceof DependencyDownloaderClassLoader ddcl) {
            sb.append("\n\nDependencies:");
            String cp = String.join("\n    ", ddcl.getDownloaded());
            sb.append("\n    ").append(cp).append("\n");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        List<String> dependencies = null;
        ClassLoader cl = getCamelContext().getApplicationContextClassLoader();
        if (cl instanceof DependencyDownloaderClassLoader) {
            @SuppressWarnings("resource")
            // The resource should not be closed as it belongs to the CamelContext and may be reused.
            DependencyDownloaderClassLoader ddcl = (DependencyDownloaderClassLoader) cl;
            dependencies = new ArrayList<>(ddcl.getDownloaded());
        }

        Boolean offline = null;
        Boolean fresh = null;
        Boolean verbose = null;
        String repos = null;
        List<DownloadRecord> downloads = null;

        MavenDependencyDownloader downloader = getCamelContext().hasService(MavenDependencyDownloader.class);
        if (downloader != null) {
            offline = !downloader.isDownload();
            fresh = downloader.isFresh();
            verbose = downloader.isVerbose();
            repos = downloader.getRepositories();
            downloads = new ArrayList<>(downloader.downloadRecords());
        }

        Response response = new Response(dependencies, offline, fresh, verbose, repos, downloads);
        return JsonRecordSupport.toJsonObject(response);
    }
}
