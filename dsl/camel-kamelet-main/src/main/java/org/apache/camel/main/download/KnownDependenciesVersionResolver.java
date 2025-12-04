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

import java.io.FileInputStream;
import java.util.Properties;

import org.apache.camel.main.util.VersionHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

public class KnownDependenciesVersionResolver extends ServiceSupport implements VersionResolver {

    private final DependencyDownloader downloader;
    private Properties properties;

    public KnownDependenciesVersionResolver(DependencyDownloader downloader) {
        this.downloader = downloader;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        String version = VersionHelper.extractCamelVersion();
        MavenArtifact ma = downloader.downloadArtifact("org.apache.camel", "camel-dependencies:pom", version);
        if (ma != null && ma.getFile() != null) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(ma.getFile());
                MavenXpp3Reader reader = new MavenXpp3Reader();
                Model model = reader.read(fis);
                properties = model.getProperties();
            } finally {
                IOHelper.close(fis);
            }
        }
    }

    @Override
    public String resolve(String version) {
        String key = StringHelper.between(version, "${", "}");
        if (key != null && properties != null) {
            version = properties.getProperty(key, version);
        }
        return version;
    }
}
