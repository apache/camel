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
package org.apache.camel.tooling.maven;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CAMEL-27342: the proxy of the Maven settings for a URL, such as OSV.dev of the CVE audit.
 */
public class MavenDownloaderProxyTest {

    @TempDir
    Path dir;

    @Test
    void proxyOfTheSettings() throws Exception {
        Path settings = dir.resolve("settings.xml");
        Files.writeString(settings, """
                <settings>
                  <localRepository>%s</localRepository>
                  <proxies>
                    <proxy>
                      <id>corp</id>
                      <active>true</active>
                      <protocol>http</protocol>
                      <host>proxy.corp.com</host>
                      <port>3128</port>
                      <username>scott</username>
                      <password>tiger</password>
                      <nonProxyHosts>localhost|*.corp.com</nonProxyHosts>
                    </proxy>
                  </proxies>
                </settings>
                """.formatted(dir.resolve("repo")));
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setMavenSettingsLocation(settings.toString());
            downloader.setOffline(true);
            downloader.build();

            MavenProxy proxy = downloader.proxyFor("https://api.osv.dev/");
            assertEquals("proxy.corp.com", proxy.host());
            assertEquals(3128, proxy.port());
            assertEquals("scott", proxy.username());
            assertEquals("tiger", proxy.password());
            assertEquals("scott@proxy.corp.com:3128", proxy.toString());
            assertEquals("localhost|*.corp.com", proxy.nonProxyHosts());

            // the hosts of nonProxyHosts are reached directly
            assertNull(downloader.proxyFor("https://nexus.corp.com/repository/osv/"));
        }
    }

    @Test
    void noProxyWithoutSettings() throws Exception {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.setMavenSettingsLocation("false");
            downloader.setOffline(true);
            downloader.build();

            assertNull(downloader.proxyFor("https://api.osv.dev/"));
        }
    }
}
