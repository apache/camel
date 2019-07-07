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

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringFileWatcherTest extends CamelSpringTestSupport {

    private File springTestFile;
    private File springTestCustomHasherFile;

    @Before
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
        Files.write(springTestFile.toPath(), "modification".getBytes(), StandardOpenOption.SYNC);
        Files.write(springTestFile.toPath(), "modification 2".getBytes(), StandardOpenOption.SYNC);
        MockEndpoint mock = getMockEndpoint("mock:springTest");
        mock.setMinimumExpectedMessageCount(2); // two MODIFY events (On Windows sometimes more)
        mock.setResultWaitTime(1000);
        mock.assertIsSatisfied();

    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/file/watch/SpringFileWatchComponentTest.xml");
    }

}
