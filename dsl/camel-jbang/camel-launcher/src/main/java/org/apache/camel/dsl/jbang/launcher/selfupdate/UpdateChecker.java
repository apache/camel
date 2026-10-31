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
import org.apache.camel.dsl.jbang.run.common.VersionHelper;

/**
 * Prints a one-line stderr notice when a background-cached check shows a newer launcher release than the one currently
 * running. Never blocks the command the user actually invoked: the network fetch that refreshes the cache runs on a
 * daemon thread, which the launcher waits for a little after the command, at most once a day (see
 * {@link #awaitRefresh()}).
 */
public final class UpdateChecker {

    private static final Duration STALE_AFTER = Duration.ofHours(24);
    // how long the launcher waits after the command for the fetch of the day
    private static final Duration REFRESH_WAIT = Duration.ofSeconds(2);
    // Cache key naming what latest_version was resolved from: absent (or "latest") for latest.properties, otherwise
    // the alias name ("lts" or "X.Y") of the install's selector pin. A cache resolved for another target is stale.
    private static final String TARGET_KEY = "target";
    private static final String LATEST_TARGET = "latest";

    // the refresh that this process started, if any
    private static volatile Thread refresh;

    private UpdateChecker() {
    }

    public static void maybeNotify(String[] args) {
        if (!isEligible(args, System.getenv("CAMEL_SELF_UPDATE_CHECK"))) {
            return;
        }
        if (InstallDetector.locate().method() != InstallDetector.InstallMethod.WEB_INSTALLER) {
            return;
        }
        // An exact pin refuses `camel self-update` outright (see SelfUpdateCommand); telling the user to run it
        // here would be advice they can't actually follow. An X.Y or LTS pin can move within its selector, so
        // check the matching alias instead of latest.properties.
        Pin pin = InstallDetector.pinnedVersion().map(Pin::parse).orElse(null);
        if (pin != null && !pin.tracksAlias()) {
            return;
        }
        String target = targetOf(pin);

        Path cacheFile = cacheFile();
        Properties cache = readCache(cacheFile);

        if (isStale(cache, System.currentTimeMillis(), target)) {
            // checked from now on, before the fetch, so that a failed or slow fetch is not tried by the next commands
            Properties checked = markChecked(cacheFile, target);
            Thread thread = new Thread(() -> refreshCache(cacheFile, checked, pin), "camel-self-update-check");
            thread.setDaemon(true);
            thread.start();
            refresh = thread;
        }

        CamelCatalog catalog = new DefaultCamelCatalog();
        String running = catalog.getCatalogVersion();
        if (shouldNotify(cache, running, target)) {
            System.err.println(formatNotice(running, cache.getProperty("latest_version")));
        }
    }

    /**
     * Waits a little (at most 2 seconds) for the refresh of the cache that {@link #maybeNotify(String[])} started in
     * this process. Most commands end before the fetch over the network would, and the JVM then stops it, so the cache
     * would never be refreshed by them. Called after the command, which is then done.
     */
    public static void awaitRefresh() {
        Thread thread = refresh;
        if (thread != null) {
            try {
                thread.join(REFRESH_WAIT.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Package-visible pure helpers, unit tested directly in UpdateCheckerTest without any file/network I/O.

    static boolean isEligible(String[] args, String checkEnvVar) {
        if (args.length > 0 && "self-update".equals(args[0])) {
            return false;
        }
        return !"false".equalsIgnoreCase(checkEnvVar);
    }

    static String targetOf(Pin pin) {
        return pin == null ? LATEST_TARGET : pin.aliasName();
    }

    static boolean isStale(Properties cache, long now) {
        return isStale(cache, now, LATEST_TARGET);
    }

    static boolean isStale(Properties cache, long now, String target) {
        if (!target.equals(cache.getProperty(TARGET_KEY, LATEST_TARGET))) {
            return true;
        }
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
        return shouldNotify(cache, running, LATEST_TARGET);
    }

    static boolean shouldNotify(Properties cache, String running, String target) {
        if (!target.equals(cache.getProperty(TARGET_KEY, LATEST_TARGET))) {
            return false;
        }
        String latest = cache.getProperty("latest_version");
        return latest != null && VersionHelper.compare(latest, running) > 0;
    }

    static String formatNotice(String running, String latest) {
        return "camel: a new version is available (" + running + " -> " + latest + "). Run 'camel self-update' to install it.";
    }

    // Package-visible for UpdateCheckerTest: the version an update notice compares against, latest.properties for an
    // unpinned install, the selector's alias for an X.Y or LTS pin. An alias outside the pinned line is rejected.
    static String resolve(ManifestFetcher fetcher, Pin pin) {
        if (pin == null) {
            return fetcher.fetchLatest().version();
        }
        String version = fetcher.fetchAlias(pin.aliasName()).version();
        if (!pin.accepts(version)) {
            throw new SelfUpdateException(
                    "manifest version (" + version + ") does not match this install, which is pinned to "
                                          + pin.describe());
        }
        return version;
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

    // Writes last_checked (and the target) before the fetch, so that a persistent failure, or a fetch that the JVM
    // stops, doesn't retry on every single invocation.
    private static Properties markChecked(Path cacheFile, String target) {
        Properties props = readCache(cacheFile);
        if (!target.equals(props.getProperty(TARGET_KEY, LATEST_TARGET))) {
            // The cached version was resolved for another target (the pin changed): never compare against it.
            props.remove("latest_version");
        }
        props.setProperty(TARGET_KEY, target);
        props.setProperty("last_checked", Long.toString(System.currentTimeMillis()));
        writeCache(cacheFile, props);
        return props;
    }

    // Runs on a daemon thread; any failure (network, timeout, parse) is swallowed, keeping whatever latest_version (if
    // any) was already cached.
    private static void refreshCache(Path cacheFile, Properties props, Pin pin) {
        try (ManifestFetcher fetcher = ManifestFetcher.fromEnvironment()) {
            props.setProperty("latest_version", resolve(fetcher, pin));
        } catch (Exception e) {
            return;
        }
        writeCache(cacheFile, props);
    }

    private static void writeCache(Path cacheFile, Properties props) {
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
