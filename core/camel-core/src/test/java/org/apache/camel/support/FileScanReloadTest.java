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
 * CAMEL-25041: the folder is scanned and each file's modification time and length compared with the previous scan, so
 * one save of several files is one set of changed files - which is what a reload needs, since a route and a property
 * saved together must be reloaded together.
 */
public class FileScanReloadTest extends ContextTestSupport {

    private Path dir;
    private FileWatcherResourceReloadStrategy strategy;

    @BeforeEach
    void createFolder() throws Exception {
        dir = Files.createTempDirectory("camel-file-scan");
        strategy = new FileWatcherResourceReloadStrategy(dir.toString(), true);
        strategy.setCamelContext(context);
        strategy.setFileFilter(f -> f.getName().endsWith(".yaml") || f.getName().endsWith(".properties"));
        // the test writes and scans at once, so no quiet period
        strategy.setStableTimeout(0);
    }

    @AfterEach
    void removeFolder() throws Exception {
        deleteRecursively(dir);
    }

    private List<String> scanNames() {
        return strategy.scan().stream().map(File::getName).sorted().toList();
    }

    @Test
    public void testOneSaveOfSeveralFilesIsOneSetOfChanges() throws Exception {
        Files.writeString(dir.resolve("shop.yaml"), "one");
        Files.writeString(dir.resolve("application.properties"), "shop.name=Camel Shop");

        // the first scan is the starting point: both files are new to it
        assertThat(scanNames()).containsExactly("application.properties", "shop.yaml");
        // nothing changed since
        assertThat(scanNames()).isEmpty();

        // the save: a route and the property it uses, together
        Files.writeString(dir.resolve("shop.yaml"), "two");
        Files.writeString(dir.resolve("application.properties"), "shop.name=Camel Shop\nshop.currency=EUR");

        // one scan, both files: the reload can apply the property before it builds the route
        assertThat(scanNames()).containsExactly("application.properties", "shop.yaml");
        assertThat(scanNames()).isEmpty();
    }

    @Test
    public void testAFileWrittenWithTheSameLengthIsStillAChange() throws Exception {
        Path f = dir.resolve("shop.yaml");
        Files.writeString(f, "one");
        scanNames();

        // rewritten with the same length, so only the modification time tells them apart
        Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 5000));
        assertThat(scanNames()).containsExactly("shop.yaml");
    }

    @Test
    public void testADeletedFileIsAChange() throws Exception {
        Files.writeString(dir.resolve("shop.yaml"), "one");
        Files.writeString(dir.resolve("other.yaml"), "one");
        scanNames();

        Files.delete(dir.resolve("other.yaml"));
        assertThat(scanNames()).containsExactly("other.yaml");
        // and only once
        assertThat(scanNames()).isEmpty();
    }

    @Test
    public void testFilesTheFilterRejectsAreNotChanges() throws Exception {
        Files.writeString(dir.resolve("shop.yaml"), "one");
        Files.writeString(dir.resolve("notes.txt"), "not mine");
        assertThat(scanNames()).containsExactly("shop.yaml");

        Files.writeString(dir.resolve("notes.txt"), "still not mine");
        assertThat(scanNames()).isEmpty();
    }

    @Test
    public void testASubdirectoryIsFoundWithoutRegisteringIt() throws Exception {
        // a tree created while running: the watch service only reported what was registered when the event happened,
        // which needed a workaround of its own (CAMEL-24862); a scan walks the tree every time
        Path sub = dir.resolve("src/main/java/com/acme");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("Bean.yaml"), "one");
        assertThat(scanNames()).containsExactly("Bean.yaml");
    }

    @Test
    public void testAModificationTimeInTheFutureIsReportedRatherThanWaitedOut() throws Exception {
        // a clock askew on a network share, or a touch -t: waiting for it to settle would mean waiting for the clock
        strategy.setStableTimeout(60_000);
        Path f = dir.resolve("shop.yaml");
        Files.writeString(f, "one");
        Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 60_000));
        assertThat(scanNames()).containsExactly("shop.yaml");
    }

    @Test
    public void testASaveStillBeingWrittenIsLeftForTheNextScan() throws Exception {
        strategy.setStableTimeout(60_000);
        Files.writeString(dir.resolve("shop.yaml"), "one");

        // just written, so not reported yet
        assertThat(scanNames()).isEmpty();

        // once it has settled it is
        strategy.setStableTimeout(0);
        assertThat(scanNames()).containsExactly("shop.yaml");
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
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
