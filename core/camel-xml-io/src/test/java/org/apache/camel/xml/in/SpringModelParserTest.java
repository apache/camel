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

package org.apache.camel.xml.in;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.model.RoutesDefinition;
import org.junit.jupiter.api.Test;

public class SpringModelParserTest {

    public static final String SPRING_NAMESPACE = "http://camel.apache.org/schema/spring";

    @Test
    public void testSpringFiles() throws Exception {
        Path dir = getResourceFolder();
        try (Stream<Path> list = Files.list(dir)) {
            List<Path> files = list.sorted()
                    .filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith("xml"))
                    .toList();
            for (Path path : files) {
                ModelParser parser = new ModelParser(Files.newInputStream(path), SPRING_NAMESPACE);
                RoutesDefinition routes = parser.parseRoutesDefinition().orElse(null);
                assertNotNull(routes);
            }
        }
    }

    @Test
    public void testSpringFilesDefault() throws Exception {
        Path dir = getResourceFolder();
        try (Stream<Path> list = Files.list(dir)) {
            List<Path> files = list.sorted()
                    .filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith("xml"))
                    .toList();
            for (Path path : files) {
                ModelParser parser = new ModelParser(Files.newInputStream(path));
                RoutesDefinition routes = parser.parseRoutesDefinition().orElse(null);
                assertNotNull(routes);
            }
        }
    }

    private Path getResourceFolder() {
        final URL resource = getClass().getClassLoader().getResource("spring/spring-convertBody.xml");
        assert resource != null : "Cannot find spring-convertBody.xml";
        String childFileString = resource.getFile();
        File parentFile = new File(childFileString).getParentFile();
        return parentFile.toPath();
    }
}
