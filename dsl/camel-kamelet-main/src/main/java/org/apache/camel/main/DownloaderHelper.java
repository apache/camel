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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

import groovy.grape.Grape;
import org.apache.camel.CamelContext;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DownloaderHelper {

    private static final Logger LOG = LoggerFactory.getLogger(DownloaderHelper.class);
    private static final String CP = System.getProperty("java.class.path");

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

        StopWatch watch = new StopWatch();
        Map<String, Object> map = new HashMap<>();
        map.put("classLoader", camelContext.getApplicationContextClassLoader());
        map.put("group", groupId);
        map.put("module", artifactId);
        map.put("version", version);
        map.put("classifier", "");

        LOG.debug("Downloading dependency: {}:{}:{}", groupId, artifactId, version);
        Grape.grab(map);

        // only report at INFO if downloading took > 1s because loading from cache is faster
        // and then it is not downloaded over the internet
        long taken = watch.taken();
        String msg = "Downloaded dependency: " + groupId + ":" + artifactId + ":" + version + " took: "
                     + TimeUtils.printDuration(taken);
        if (taken < 1000) {
            LOG.debug(msg);
        } else {
            LOG.info(msg);
        }
    }

    public static boolean alreadyOnClasspath(CamelContext camelContext, String artifactId, String version) {
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
                        // already on classpath
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
