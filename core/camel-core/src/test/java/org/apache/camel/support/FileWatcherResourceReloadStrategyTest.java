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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.impl.engine.DefaultCompileStrategy;
import org.apache.camel.spi.CompileStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-24862: the file watcher, when recursive, watches a directory created while it runs, and it ignores the compile
 * work directory where the runtime writes the class files it compiles.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Runs only local: the JDK watch service on a CI file system is too slow to time")
public class FileWatcherResourceReloadStrategyTest extends ContextTestSupport {

    @TempDir
    Path dir;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testNewDirectoryIsWatchedAndCompileWorkDirIsNot() throws Exception {
        Path compile = dir.resolve(".camel-jbang/compile");
        Files.createDirectories(compile);
        CompileStrategy cs = new DefaultCompileStrategy();
        cs.setWorkDir(compile.toString());
        context.getCamelContextExtension().addContextPlugin(CompileStrategy.class, cs);

        List<String> reloaded = new CopyOnWriteArrayList<>();
        FileWatcherResourceReloadStrategy strategy = new FileWatcherResourceReloadStrategy(dir.toString(), true);
        strategy.setCamelContext(context);
        strategy.setResourceReload((name, resource) -> reloaded.add(name));
        strategy.start();
        try {
            // a tree created after the start, with its first file in it
            Path tree = dir.resolve("src/main/java/camel/example");
            Files.createDirectories(tree);
            Files.writeString(tree.resolve("OrderNumber.java"), "package camel.example; public class OrderNumber {}");
            await().atMost(java.time.Duration.ofSeconds(30))
                    .until(() -> reloaded.stream().anyMatch(n -> n.endsWith("OrderNumber.java")));

            // a file written later into the new tree is seen as well: the directory is watched now
            Files.writeString(tree.resolve("Other.java"), "package camel.example; public class Other {}");
            await().atMost(java.time.Duration.ofSeconds(30))
                    .until(() -> reloaded.stream().anyMatch(n -> n.endsWith("Other.java")));

            // a class file the runtime writes into the compile work dir is not a change
            Files.createDirectories(compile.resolve("camel/example"));
            Files.writeString(compile.resolve("camel/example/OrderNumber.class"), "bytecode");
            Files.writeString(dir.resolve("marker.txt"), "after the class file");
            await().atMost(java.time.Duration.ofSeconds(30))
                    .until(() -> reloaded.stream().anyMatch(n -> n.endsWith("marker.txt")));
            assertFalse(reloaded.stream().anyMatch(n -> n.endsWith(".class")), "class files reloaded: " + reloaded);
            assertTrue(reloaded.stream().noneMatch(n -> n.contains(".camel-jbang")), reloaded.toString());
        } finally {
            strategy.stop();
        }
    }
}
