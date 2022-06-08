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
package org.apache.camel.main;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

import groovy.grape.Grape;
import org.apache.camel.CamelContext;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For downloading dependencies.
 */
public final class DownloaderHelper {

    private static final Logger LOG = LoggerFactory.getLogger(DownloaderHelper.class);
    private static final String CP = System.getProperty("java.class.path");

    private static final DownloadThreadPool DOWNLOAD_THREAD_POOL = new DownloadThreadPool();

    private DownloaderHelper() {
    }

    public static void downloadDependency(CamelContext camelContext, String groupId, String artifactId, String version) {

        // trigger listener
        DownloadListener listener = camelContext.getExtension(DownloadListener.class);
        if (listener != null) {
            listener.onDownloadDependency(groupId, artifactId, version);
        }

        // when running jbang directly then the CP has some existing camel components
        // that essentially is not needed to be downloaded, but we need the listener to trigger
        // to capture that the GAV is required for running the application
        if (CP != null) {
            // is it already on classpath
            String target = artifactId;
            if (version != null) {
                target = target + "-" + version;
            }
            if (CP.contains(target)) {
                // already on classpath
                return;
            }
        }

        Map<String, Object> map = new HashMap<>();
        map.put("classLoader", camelContext.getApplicationContextClassLoader());
        map.put("group", groupId);
        map.put("module", artifactId);
        map.put("version", version);
        map.put("classifier", "");
        //        map.put("excludes", Collections.singletonList(Map.of("group", "com.atlassian.sal", "module", "sal-api")));

        String gav = groupId + ":" + artifactId + ":" + version;
        DOWNLOAD_THREAD_POOL.download(LOG, () -> {
            LOG.debug("Downloading: {}", gav);
            Grape.grab(map);
        }, gav);
    }

    public static boolean alreadyOnClasspath(CamelContext camelContext, String groupId, String artifactId, String version) {
        // if no artifact then regard this as okay
        if (artifactId == null) {
            return true;
        }

        String target = artifactId;
        if (version != null) {
            target = target + "-" + version;
        }

        if (camelContext.getApplicationContextClassLoader() != null) {
            ClassLoader cl = camelContext.getApplicationContextClassLoader();
            if (cl instanceof URLClassLoader) {
                URLClassLoader ucl = (URLClassLoader) cl;
                for (URL u : ucl.getURLs()) {
                    String s = u.toString();
                    if (s.contains(target)) {
                        // trigger listener
                        DownloadListener listener = camelContext.getExtension(DownloadListener.class);
                        if (listener != null) {
                            listener.onAlreadyDownloadedDependency(groupId, artifactId, version);
                        }
                        // already on classpath
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void prepareDownloader(CamelContext camelContext, String repos, boolean download, boolean verbose)
            throws Exception {
        InputStream is = download
                ? DownloaderHelper.class.getResourceAsStream("/camelGrapeConfig.xml")
                : DownloaderHelper.class.getResourceAsStream("/localGrapeConfig.xml");
        if (is != null) {
            String xml = IOHelper.loadText(is);
            if (download && repos != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("            <!-- custom repositories -->");
                int i = 0;
                for (String repo : repos.split(",")) {
                    i++;
                    sb.append(String.format("\n            <url name=\"custom%s\" m2compatible=\"true\">", i));
                    sb.append(String.format(
                            "\n                <artifact pattern=\"%s/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]\"/>",
                            repo));
                    sb.append(String.format("\n            </url>"));
                }
                xml = xml.replace("            <!-- @repos@ -->", sb.toString());
            }

            // save file to local disk and point grape to use this
            File out = new File(".camel-jbang/camelGrapeConfig.xml");
            IOHelper.writeText(xml, out);

            // Grape should use our custom configuration file
            System.setProperty("grape.config", out.getAbsolutePath());
            if (verbose) {
                System.setProperty("groovy.grape.report.downloads", "true");
            }

            IOHelper.close(is);
        }
    }

}
