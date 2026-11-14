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
package org.apache.camel.support;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25670: dev mode with a source directory watches every file under it, so routes that write data files into a
 * folder under it reloaded themselves, again and again. A pattern part that starts with ! leaves files out, and a
 * folder it matches as a whole is not scanned; repeated reloads for files under one folder log a warning that names it.
 */
public class RouteWatcherReloadDataDirTest extends ContextTestSupport {

    private Path dir;
    private RouteWatcherReloadStrategy strategy;

    @BeforeEach
    void createFolder() throws Exception {
        dir = Files.createTempDirectory("camel-data-dir");
        Files.writeString(dir.resolve("shop.camel.yaml"), "one");
        Files.createDirectories(dir.resolve("inbox/done"));
        Files.writeString(dir.resolve("inbox/order-1.json"), "{}");
        Files.writeString(dir.resolve("inbox/done/order-0.json"), "{}");
    }

    @AfterEach
    void removeFolder() throws Exception {
        if (strategy != null) {
            strategy.stop();
        }
        deleteRecursively(dir);
    }

    private void start(String pattern) {
        strategy = new RouteWatcherReloadStrategy(dir.toString(), true);
        strategy.setCamelContext(context);
        strategy.setPattern(pattern);
        // the test scans itself, at once
        strategy.setScheduler(false);
        strategy.setStableTimeout(0);
        strategy.start();
    }

    private List<String> scanNames() {
        return strategy.scan().stream().map(File::getName).sorted().toList();
    }

    @Test
    public void testEveryFileIsWatchedWithoutAnExclude() throws Exception {
        start("*");

        assertThat(scanNames()).containsExactly("order-0.json", "order-1.json", "shop.camel.yaml");
        // the routes write an order into the inbox: a change, which reloads the routes
        Files.writeString(dir.resolve("inbox/order-2.json"), "{}");
        assertThat(scanNames()).containsExactly("order-2.json");
    }

    @Test
    public void testAnExcludedFolderIsNotWatched() throws Exception {
        start("*,!inbox/**");

        assertThat(scanNames()).containsExactly("shop.camel.yaml");
        assertThat(strategy.isExcludedDirectory(dir.resolve("inbox"))).isTrue();
        assertThat(strategy.isExcludedDirectory(dir.resolve("inbox/done"))).isTrue();

        // what the routes write and move in the inbox is data: no change
        Files.writeString(dir.resolve("inbox/order-2.json"), "{}");
        Files.move(dir.resolve("inbox/order-1.json"), dir.resolve("inbox/done/order-1.json"));
        assertThat(scanNames()).isEmpty();

        // the route is still watched
        Files.writeString(dir.resolve("shop.camel.yaml"), "one and two");
        assertThat(scanNames()).containsExactly("shop.camel.yaml");
    }

    @Test
    public void testAnExcludeWithoutTheStarsIsTheSameFolder() throws Exception {
        start("*,!inbox");

        assertThat(strategy.isExcludedDirectory(dir.resolve("inbox"))).isTrue();
        assertThat(scanNames()).containsExactly("shop.camel.yaml");
    }

    @Test
    public void testAFilePatternExcludeLeavesOutFilesNotFolders() throws Exception {
        start("*,!*.json");

        // the folder is still walked, for the files it holds that are not json
        assertThat(strategy.isExcludedDirectory(dir.resolve("inbox"))).isFalse();
        Files.writeString(dir.resolve("inbox/readme.txt"), "a text");
        assertThat(scanNames()).containsExactly("readme.txt", "shop.camel.yaml");
    }

    @Test
    public void testTheFilesARunWritesAreNotWatched() throws Exception {
        // CAMEL-26930: camel dev > run.log in the project reloaded all the routes every 2 seconds; the pattern of
        // camel run --dev with a source directory leaves out logs, the build output, git and the Camel folders
        start("*,!test/**,!*.log,!target/**,!.git/**,!.camel/**,!.camel-jbang/**");
        assertThat(scanNames()).containsExactly("order-0.json", "order-1.json", "shop.camel.yaml");

        Files.writeString(dir.resolve("run.log"), "a line");
        Files.createDirectories(dir.resolve("logs"));
        Files.writeString(dir.resolve("logs/app.log"), "a line");
        Files.createDirectories(dir.resolve("target/classes"));
        Files.writeString(dir.resolve("target/classes/Foo.class"), "bytes");
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".git/index"), "index");
        Files.createDirectories(dir.resolve(".camel"));
        Files.writeString(dir.resolve(".camel/order-1.json"), "{}");
        Files.createDirectories(dir.resolve(".camel-jbang"));
        Files.writeString(dir.resolve(".camel-jbang/app.lock"), "lock");
        assertThat(scanNames()).isEmpty();

        // the route is still watched
        Files.writeString(dir.resolve("shop.camel.yaml"), "one and two");
        assertThat(scanNames()).containsExactly("shop.camel.yaml");
    }

    @Test
    public void testTestFolderIsNotWatched() throws Exception {
        // CAMEL-26494: the pattern of camel run --dev with a source directory leaves the test folder out, with the
        // Citrus tests of the project (whatever their name) and their data
        Files.createDirectories(dir.resolve("test/payloads"));
        Files.writeString(dir.resolve("test/orders.citrus.it.yaml"), "a test");
        Files.writeString(dir.resolve("test/orders.citrus-it.yaml"), "a test");
        Files.writeString(dir.resolve("test/orders.feature"), "a test");
        Files.writeString(dir.resolve("test/payloads/order.json"), "{}");
        start("*,!test/**,!inbox/**");

        assertThat(strategy.isExcludedDirectory(dir.resolve("test"))).isTrue();
        assertThat(scanNames()).containsExactly("shop.camel.yaml");

        // editing or adding a test, or its data, is no change of the routes
        Files.writeString(dir.resolve("test/orders.citrus.it.yaml"), "a test, changed");
        Files.writeString(dir.resolve("test/payments.citrus.it.yaml"), "another test");
        Files.writeString(dir.resolve("test/payloads/order.json"), "{\"id\":1}");
        assertThat(scanNames()).isEmpty();

        // a route in another folder is still watched, and so is a folder whose name only starts with test
        Files.createDirectories(dir.resolve("routes"));
        Files.createDirectories(dir.resolve("testing"));
        Files.writeString(dir.resolve("routes/stub.camel.yaml"), "a stub");
        Files.writeString(dir.resolve("testing/other.camel.yaml"), "a route");
        assertThat(scanNames()).containsExactly("other.camel.yaml", "stub.camel.yaml");
    }

    @Test
    public void testRepeatedReloadsForOneFolderWarnOnce() {
        start("*");
        List<File> data = List.of(dir.resolve("inbox/order-1.json").toFile(), dir.resolve("inbox/done/order-0.json").toFile());

        long now = 1_000_000;
        for (int i = 0; i < RouteWatcherReloadStrategy.LOOP_RELOADS - 1; i++) {
            assertThat(strategy.checkReloadLoop(data, now + i * 2000L)).isFalse();
        }
        // the fifth reload within 15 seconds, all for files under inbox
        assertThat(strategy.checkReloadLoop(data, now + 8000)).isTrue();
        // once per folder
        assertThat(strategy.checkReloadLoop(data, now + 10000)).isFalse();
    }

    @Test
    public void testNoWarningWhenTheReloadsAreSlowOrForSources() {
        start("*");
        List<File> data = List.of(dir.resolve("inbox/order-1.json").toFile());
        List<File> route = List.of(dir.resolve("shop.camel.yaml").toFile());
        List<File> properties = List.of(dir.resolve("inbox/application.properties").toFile());

        // five reloads, but over more than 15 seconds: a person dropping files now and then
        for (int i = 0; i < RouteWatcherReloadStrategy.LOOP_RELOADS; i++) {
            assertThat(strategy.checkReloadLoop(data, i * 5000L)).isFalse();
        }
        // a change of a file at the top (a route being edited) or of a properties file starts the count again
        long now = 100_000;
        for (int i = 0; i < RouteWatcherReloadStrategy.LOOP_RELOADS - 1; i++) {
            assertThat(strategy.checkReloadLoop(data, now + i * 1000L)).isFalse();
        }
        assertThat(strategy.checkReloadLoop(route, now + 4000)).isFalse();
        for (int i = 0; i < RouteWatcherReloadStrategy.LOOP_RELOADS - 1; i++) {
            assertThat(strategy.checkReloadLoop(data, now + 5000 + i * 1000L)).isFalse();
        }
        assertThat(strategy.checkReloadLoop(properties, now + 9000)).isFalse();
        for (int i = 0; i < RouteWatcherReloadStrategy.LOOP_RELOADS - 1; i++) {
            assertThat(strategy.checkReloadLoop(data, now + 10000 + i * 1000L)).isFalse();
        }
    }

    @Test
    public void testNoWarningWhenTheFilesAreUnderDifferentFolders() {
        start("*");
        List<File> inbox = List.of(dir.resolve("inbox/order-1.json").toFile());
        List<File> outbox = List.of(dir.resolve("outbox/order-1.json").toFile());

        long now = 1_000_000;
        for (int i = 0; i < RouteWatcherReloadStrategy.LOOP_RELOADS; i++) {
            assertThat(strategy.checkReloadLoop(i % 2 == 0 ? inbox : outbox, now + i * 1000L)).isFalse();
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
