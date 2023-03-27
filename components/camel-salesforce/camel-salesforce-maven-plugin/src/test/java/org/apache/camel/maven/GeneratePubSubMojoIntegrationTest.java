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
package org.apache.camel.maven;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.JavaFileObject;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.camel.maven.AbstractSalesforceMojoIntegrationTest.setup;
import static org.assertj.core.api.Assertions.assertThat;

public class GeneratePubSubMojoIntegrationTest {

    private static final String TEST_LOGIN_PROPERTIES = "../test-salesforce-login.properties";

    @TempDir
    public Path temp;

    @Test
    public void testExecute() throws Exception {
        final GeneratePubSubMojo mojo = createMojo();

        // generate code
        mojo.execute();

        // validate generated code check that it was generated
        final Path packagePath = temp.resolve("com").resolve("sforce").resolve("eventbus");
        assertThat(packagePath).as("Package directory was not created").exists();

        // test that the generated sources can be compiled
        try (Stream<Path> list = Files.list(packagePath)) {
            final List<JavaFileObject> sources = list.map(p -> {
                try {
                    return JavaFileObjects.forResource(p.toUri().toURL());
                } catch (final MalformedURLException e) {
                    throw new IllegalArgumentException(e);
                }
            }).collect(Collectors.toList());
            final Compilation compilation = Compiler.javac().compile(sources);
            assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
        }
    }

    GeneratePubSubMojo createMojo() throws IOException {
        final GeneratePubSubMojo mojo = new GeneratePubSubMojo();

        // set login properties
        setup(mojo);

        // set additional properties specific to this Mojo
        try (final InputStream stream = new FileInputStream(TEST_LOGIN_PROPERTIES)) {
            final Properties properties = new Properties();
            properties.load(stream);
            mojo.pubSubHost = properties.getProperty("salesforce.pubsub.host");
            mojo.pubSubPort = Integer.valueOf(properties.getProperty("salesforce.pubsub.port"));
            mojo.topics = new String[] { "/event/BatchApexErrorEvent" };
        }
        mojo.outputDirectory = temp.toFile();
        return mojo;
    }
}
