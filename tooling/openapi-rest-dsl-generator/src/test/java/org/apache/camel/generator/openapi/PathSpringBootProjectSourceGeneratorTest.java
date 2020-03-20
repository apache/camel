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
package org.apache.camel.generator.openapi;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PathSpringBootProjectSourceGeneratorTest {

    @Test
    public void shouldGenerateSourceCodeWithDefaults() throws IOException, URISyntaxException {
        final Path path = new File("target/generated-sources").toPath();
        SpringBootProjectSourceCodeGenerator.generator().withPackageName("com.foo").generate(path);
        final String generatedContent = new String(Files.readAllBytes(Paths.get("target/generated-sources/com/foo/CamelRestController.java")),
            StandardCharsets.UTF_8);

        final URI file = PathSpringBootProjectSourceGeneratorTest.class.getResource("/SpringBootRestController.txt").toURI();
        final String expectedContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

}
