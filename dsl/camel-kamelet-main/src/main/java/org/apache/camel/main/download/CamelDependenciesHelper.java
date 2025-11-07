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

import org.apache.camel.CamelContext;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;

public class CamelDependenciesHelper {

    /**
     * Resolve the version of a given dependency defined in the camel-dependencies POM file, which has a list of all the
     * 3rd-party dependencies used by Camel components.
     */
    public static String dependencyVersion(CamelContext context, String key) {
        MavenDependencyDownloader downloader = context.hasService(MavenDependencyDownloader.class);
        if (downloader != null) {
            MavenArtifact ma = downloader.downloadArtifact("org.apache.camel", "camel-dependencies:pom", context.getVersion());
            if (ma != null && ma.getFile() != null) {
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(ma.getFile());
                    String text = IOHelper.loadText(fis);
                    return StringHelper.between(text, "<" + key + ">", "</" + key + ">");
                } catch (Exception e) {
                    // ignore
                } finally {
                    IOHelper.close(fis);
                }
            }
        }
        return null;
    }
}
