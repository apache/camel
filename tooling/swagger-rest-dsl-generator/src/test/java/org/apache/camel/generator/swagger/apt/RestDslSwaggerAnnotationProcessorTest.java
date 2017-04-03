/**
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
package org.apache.camel.generator.swagger.apt;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.google.common.io.Resources;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;

import org.junit.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

public class RestDslSwaggerAnnotationProcessorTest {

    @Test
    public void shouldGenerateRestDslWithDestinationGenerator() throws IOException {
        final URL helloWorld = Resources.getResource("test/HelloWorld.java");

        // path to src/test/resources
        final String sourcePath = new File(helloWorld.getFile()).getParentFile().getParent();

        final Compilation compilation = javac().withOptions("-sourcepath", sourcePath)
            .withProcessors(new RestDslSwaggerAnnotationProcessor()).compile(
                JavaFileObjects.forSourceLines("HelloWorld", Resources.readLines(helloWorld, StandardCharsets.UTF_8)));

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("com/example/MyRestRoute")
            .hasSourceEquivalentTo(JavaFileObjects.forSourceLines("com.example.MyRestRoute",
                Resources.readLines(Resources.getResource("MyRestRoute.txt"), StandardCharsets.UTF_8)));
    }

    @Test
    public void shouldGenerateRestDslWithOnlySpecification() throws IOException {
        final Compilation compilation = javac().withProcessors(new RestDslSwaggerAnnotationProcessor())
            .compile(JavaFileObjects.forSourceString("HelloWorld",
                "@org.apache.camel.generator.swagger.apt.SwaggerRestDsl(specificationUri=\"petstore.json\")\n"
                    + "final class HelloWorld {}"));

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("io/swagger/petstore/SwaggerPetstore")
            .hasSourceEquivalentTo(JavaFileObjects.forSourceLines("io.swagger.petstore.SwaggerPetstore",
                Resources.readLines(Resources.getResource("SwaggerPetstore.txt"), StandardCharsets.UTF_8)));
    }

    @Test
    public void shouldNotInterfereWithNonAnnotatedClasses() {
        final Compilation compilation = javac().withProcessors(new RestDslSwaggerAnnotationProcessor())
            .compile(JavaFileObjects.forSourceString("HelloWorld", "final class HelloWorld {}"));

        assertThat(compilation).succeeded();
    }
}
