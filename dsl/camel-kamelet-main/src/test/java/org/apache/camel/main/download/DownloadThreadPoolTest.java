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
package org.apache.camel.main.download;

import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.tooling.maven.MavenResolutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DownloadThreadPoolTest {

    private static final Logger LOG = LoggerFactory.getLogger(DownloadThreadPoolTest.class);

    private static final String REASON = "The following artifacts could not be resolved: com.example:missing:jar:1.0 (absent): "
                                         + "Could not find artifact com.example:missing:jar:1.0 in central (https://repo1.maven.org/maven2)";

    private final DownloadException failure = new DownloadException(
            "Cannot resolve dependencies in https://repo1.maven.org/maven2", new MavenResolutionException(REASON));

    private DownloadThreadPool pool;

    @BeforeEach
    void setUp() {
        pool = new DownloadThreadPool(new MavenDependencyDownloader());
        ServiceHelper.startService(pool);
    }

    @AfterEach
    void tearDown() {
        ServiceHelper.stopAndShutdownService(pool);
    }

    @Test
    void failedDownloadIsLogged() {
        assertDoesNotThrow(() -> pool.download(LOG, () -> {
            throw failure;
        }, "com.example:missing:1.0"));
    }

    @Test
    void failedRequiredDownloadFailsWithTheReasonOfMavenResolver() {
        DownloadException e = assertThrows(DownloadException.class, () -> pool.download(LOG, () -> {
            throw failure;
        }, "com.example:missing:1.0", true));

        assertEquals("Cannot download dependency: com.example:missing:1.0 due to: " + REASON, e.getMessage());
        assertSame(failure, e.getCause());
    }

    @Test
    void failedRequiredDownloadWithoutMavenResolverFailsWithTheMessageOfTheCause() {
        DownloadException e = assertThrows(DownloadException.class, () -> pool.download(LOG, () -> {
            throw new IllegalStateException("Disk full");
        }, "com.example:missing:1.0", true));

        assertEquals("Cannot download dependency: com.example:missing:1.0 due to: Disk full", e.getMessage());
    }
}
