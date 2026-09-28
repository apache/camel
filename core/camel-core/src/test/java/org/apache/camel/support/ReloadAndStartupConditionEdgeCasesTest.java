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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.spi.StartupCondition;
import org.apache.camel.support.startup.DefaultStartupConditionStrategy;
import org.apache.camel.support.startup.EnvStartupCondition;
import org.apache.camel.support.startup.FileStartupCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReloadAndStartupConditionEdgeCasesTest extends ContextTestSupport {

    @TempDir
    Path dir;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Runs only local: the file watcher on a CI file system is too slow to time")
    public void testReloadContinuesAfterError() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        FileWatcherResourceReloadStrategy strategy = new FileWatcherResourceReloadStrategy(dir.toString());
        strategy.setCamelContext(context);
        strategy.setResourceReload((name, resource) -> {
            if (calls.incrementAndGet() == 1) {
                throw new ExceptionInInitializerError("boom");
            }
        });
        strategy.start();
        try {
            Files.writeString(dir.resolve("a.txt"), "one");
            await().atMost(Duration.ofSeconds(30)).until(() -> calls.get() >= 1);
            Files.writeString(dir.resolve("b.txt"), "two");
            await().atMost(Duration.ofSeconds(30)).until(() -> calls.get() >= 2);
        } finally {
            strategy.stop();
        }
    }

    @Test
    public void testReloadPatternWithDirectory() throws Exception {
        File sub = dir.resolve("sub").toFile();
        assertTrue(sub.mkdirs());
        File file = new File(sub, "s.yaml");
        Files.writeString(file.toPath(), "- route:");

        RouteWatcherReloadStrategy strategy = new RouteWatcherReloadStrategy(dir.toString(), true);
        strategy.setPattern("sub/*.yaml, other/*.xml");
        strategy.setCamelContext(context);
        strategy.doStart();
        try {
            assertTrue(strategy.getFileFilter().accept(file));
            assertFalse(strategy.getFileFilter().accept(new File(dir.toFile(), "s.yaml")));
        } finally {
            strategy.doStop();
        }

        // a pattern without a directory still matches files in sub directories
        strategy = new RouteWatcherReloadStrategy(dir.toString(), true);
        strategy.setPattern("*.yaml");
        strategy.setCamelContext(context);
        strategy.doStart();
        try {
            assertTrue(strategy.getFileFilter().accept(file));
        } finally {
            strategy.doStop();
        }
    }

    private static class LaterCondition implements StartupCondition {
        private final long readyAt;

        LaterCondition(long delay) {
            this.readyAt = System.currentTimeMillis() + delay;
        }

        @Override
        public String getName() {
            return "Later";
        }

        @Override
        public boolean canContinue(CamelContext camelContext) {
            return System.currentTimeMillis() >= readyAt;
        }
    }

    @Test
    public void testStartupConditionReportsFailingCondition() throws Exception {
        Path exists = Files.writeString(dir.resolve("exists.txt"), "yes");
        DefaultStartupConditionStrategy scs = new DefaultStartupConditionStrategy();
        scs.setCamelContext(context);
        scs.setEnabled(true);
        scs.setTimeout(200);
        scs.setInterval(50);
        scs.setOnTimeout("fail");
        scs.addStartupCondition(new EnvStartupCondition("CAMEL_NO_SUCH_ENV_FOR_TEST"));
        scs.addStartupCondition(new FileStartupCondition(exists.toString()));

        VetoCamelContextStartException e = assertThrows(VetoCamelContextStartException.class, scs::checkStartupConditions);
        assertTrue(e.getMessage().contains("ENV"), e.getMessage());
        assertFalse(e.getMessage().contains("File"), e.getMessage());
    }

    @Test
    public void testStartupConditionCheckedWithZeroTimeout() throws Exception {
        Path exists = Files.writeString(dir.resolve("exists.txt"), "yes");
        DefaultStartupConditionStrategy scs = new DefaultStartupConditionStrategy();
        scs.setCamelContext(context);
        scs.setEnabled(true);
        scs.setTimeout(0);
        scs.setOnTimeout("fail");
        scs.addStartupCondition(new FileStartupCondition(exists.toString()));

        assertDoesNotThrow(scs::checkStartupConditions);
    }

    @Test
    public void testStartupConditionCheckedAfterLastWait() {
        DefaultStartupConditionStrategy scs = new DefaultStartupConditionStrategy();
        scs.setCamelContext(context);
        scs.setEnabled(true);
        scs.setTimeout(1000);
        scs.setInterval(1000);
        scs.setOnTimeout("fail");
        scs.addStartupCondition(new LaterCondition(500));

        assertDoesNotThrow(scs::checkStartupConditions);
    }
}
