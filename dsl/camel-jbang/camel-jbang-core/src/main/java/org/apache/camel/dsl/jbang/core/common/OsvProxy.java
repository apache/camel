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

import java.util.Map;

import org.apache.camel.catalog.cve.HttpProxy;
import org.apache.camel.catalog.cve.OsvClient;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenProxy;

/**
 * The proxy the CVE audit reaches OSV.dev through (CAMEL-27342): the active proxy of the Maven settings
 * ({@code ~/.m2/settings.xml} or {@code --maven-settings}, with its username, its password decrypted with the
 * settings-security.xml and its nonProxyHosts), else the {@code HTTPS_PROXY} / {@code NO_PROXY} environment variables
 * of a CI runner, else null for the proxy settings of the JVM ({@code -Dhttps.proxyHost}).
 */
public final class OsvProxy {

    private OsvProxy() {
    }

    /** The proxy, from the Maven settings of a built downloader, else from the environment. */
    public static HttpProxy resolve(MavenDownloaderImpl downloader, Map<String, String> env) {
        MavenProxy maven = downloader != null ? downloader.proxyFor(OsvClient.API_URL) : null;
        if (maven != null) {
            // with the nonProxyHosts: the TUI fetches other URLs (the documentation) through the same proxy
            return new HttpProxy(
                    maven.host(), maven.port(), maven.username(), maven.password(),
                    HttpProxy.splitHosts(maven.nonProxyHosts()));
        }
        return HttpProxy.fromEnvironment(env);
    }

    /**
     * The proxy, from the given Maven settings (null for {@code ~/.m2/settings.xml}, {@code false} for none), else from
     * the environment. Only the settings are read: nothing is downloaded.
     */
    public static HttpProxy resolve(String mavenSettings, String mavenSettingsSecurity, Map<String, String> env) {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setMavenSettingsLocation(mavenSettings);
            downloader.setMavenSettingsSecurityLocation(mavenSettingsSecurity);
            downloader.build();
            return resolve(downloader, env);
        } catch (Exception e) {
            // unreadable settings: as without them
            return HttpProxy.fromEnvironment(env);
        }
    }
}
