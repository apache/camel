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
package org.apache.camel.dsl.jbang.launcher.selfupdate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.InstallDetector;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;

/**
 * Prints a one-line stderr notice when a background-cached check shows a newer launcher release than the one currently
 * running. Never blocks the command the user actually invoked: the network fetch that refreshes the cache runs on a
 * daemon thread and is simply retried on a later invocation if the JVM exits first.
 */
public final class UpdateChecker {

    private static final Duration STALE_AFTER = Duration.ofHours(24);

    private UpdateChecker() {
    }

    public static void maybeNotify(String[] args) {
        if (!isEligible(args, System.getenv("CAMEL_SELF_UPDATE_CHECK"))) {
            return;
        }
        if (InstallDetector.locate().method() != InstallDetector.InstallMethod.WEB_INSTALLER) {
            return;
        }

        Path cacheFile = cacheFile();
        Properties cache = readCache(cacheFile);

        if (isStale(cache, System.currentTimeMillis())) {
            Thread refresh = new Thread(() -> refreshCache(cacheFile), "camel-self-update-check");
            refresh.setDaemon(true);
            refresh.start();
        }

        CamelCatalog catalog = new DefaultCamelCatalog();
        String running = catalog.getCatalogVersion();
        if (shouldNotify(cache, running)) {
            System.err.println(formatNotice(running, cache.getProperty("latest_version")));
        }
    }

    // Package-visible pure helpers, unit tested directly in UpdateCheckerTest without any file/network I/O.

    static boolean isEligible(String[] args, String checkEnvVar) {
        if (args.length > 0 && "self-update".equals(args[0])) {
            return false;
        }
        return !"false".equalsIgnoreCase(checkEnvVar);
    }

    static boolean isStale(Properties cache, long now) {
        String lastChecked = cache.getProperty("last_checked");
        if (lastChecked == null) {
            return true;
        }
        try {
            return now - Long.parseLong(lastChecked) > STALE_AFTER.toMillis();
        } catch (NumberFormatException e) {
            return true;
        }
    }

    static boolean shouldNotify(Properties cache, String running) {
        String latest = cache.getProperty("latest_version");
        return latest != null && VersionHelper.compare(latest, running) > 0;
    }

    static String formatNotice(String running, String latest) {
        return "camel: a new version is available (" + running + " -> " + latest + "). Run 'camel self-update' to install it.";
    }

    private static Path cacheFile() {
        return InstallDetector.webInstallerVersionsRoot().getParent().resolve("update-check.properties");
    }

    private static Properties readCache(Path cacheFile) {
        Properties props = new Properties();
        if (Files.exists(cacheFile)) {
            try (InputStream in = Files.newInputStream(cacheFile)) {
                props.load(in);
            } catch (IOException e) {
                // Corrupt/unreadable cache: treated the same as "no cache", refreshed on the next stale check.
            }
        }
        return props;
    }

    // Runs on a daemon thread; any failure (network, timeout, parse) is swallowed, but last_checked is still
    // updated so a persistent failure doesn't retry on every single invocation.
    private static void refreshCache(Path cacheFile) {
        Properties props = readCache(cacheFile);
        props.setProperty("last_checked", Long.toString(System.currentTimeMillis()));
        try {
            ManifestFetcher.Manifest manifest = ManifestFetcher.fromEnvironment().fetchLatest();
            props.setProperty("latest_version", manifest.version());
        } catch (Exception e) {
            // Network/timeout/parse failure: keep whatever latest_version (if any) was already cached.
        }
        try {
            Files.createDirectories(cacheFile.getParent());
            try (OutputStream out = Files.newOutputStream(cacheFile)) {
                props.store(out, null);
            }
        } catch (IOException e) {
            // Best-effort cache write; a failure here just means the next invocation re-checks immediately.
        }
    }
}
