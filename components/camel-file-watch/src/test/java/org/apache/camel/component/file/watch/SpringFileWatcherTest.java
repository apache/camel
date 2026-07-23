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
package org.apache.camel.component.file.watch;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit6.CamelSpringTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringFileWatcherTest extends CamelSpringTestSupport {

    private File springTestFile;
    private File springTestCustomHasherFile;

    @BeforeEach
    public void createTestFiles() throws Exception {
        Files.createDirectories(Paths.get("target/fileWatchSpringTest"));
        Files.createDirectories(Paths.get("target/fileWatchSpringTestCustomHasher"));
        springTestFile = new File("target/fileWatchSpringTest", UUID.randomUUID().toString());
        springTestCustomHasherFile = new File("target/fileWatchSpringTestCustomHasher", UUID.randomUUID().toString());
        springTestFile.createNewFile();
        springTestCustomHasherFile.createNewFile();
    }

    @Test
    public void testDefaultConfig() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:springTest");
        // Use minimum count to tolerate CREATE events from file creation in @BeforeEach,
        // which may be delivered on JDK 25+ due to faster WatchService initialization
        mock.expectedMinimumMessageCount(2);
        mock.setResultWaitTime(2000);

        Files.write(springTestFile.toPath(), "modification".getBytes(), StandardOpenOption.SYNC);
        // Wait for the watcher to process and hash the first write before the second one,
        // so the file hasher can distinguish the two modifications as separate events
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> mock.getReceivedCounter() >= 1);
        Files.write(springTestFile.toPath(), "modification 2".getBytes(), StandardOpenOption.SYNC);

        mock.assertIsSatisfied();
    }

    @Test
    public void testCustomHasher() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:springTestCustomHasher");
        mock.setExpectedCount(1); // We passed dummy TestHasher which returns constant hashcode. This should cause, that second MODIFY event is discarded
        mock.setResultWaitTime(2000);

        Files.write(springTestCustomHasherFile.toPath(), "first modification".getBytes(), StandardOpenOption.SYNC);
        // Wait for the watcher to process and hash the first write before the second one,
        // so the file hasher evaluates them as separate events (deduplicating via constant hash)
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> mock.getReceivedCounter() >= 1);
        Files.write(springTestCustomHasherFile.toPath(), "second modification".getBytes(), StandardOpenOption.SYNC);

        mock.assertIsSatisfied();
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/file/watch/SpringFileWatchComponentTest.xml");
    }

}
