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
package org.apache.camel.maven.packaging;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.JBangModel;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrepareCamelJBangMojoTest {

    @TempDir
    Path dir;

    @Test
    void aDescriptionJoinedWithPlusIsOneText() throws Exception {
        Path source = dir.resolve("Constants.java");
        Files.writeString(source, """
                package org.example;

                import org.apache.camel.spi.Metadata;

                public final class Constants {

                    @Metadata(description = "Folders that hold data the routes read or write,"
                                            + " not sources: a change in them does not reload the routes.",
                              javaType = "String", label = "advanced")
                    public static final String DATA_DIR = "camel.jbang.dataDir";

                    @Metadata(description = "A description with a + inside, and \\"quotes\\"", javaType = "String")
                    public static final String PLAIN = "camel.jbang.plain";
                }
                """);

        List<JBangModel.JBangOptionModel> options = PrepareCamelJBangMojo.parseConfigurationSource(source.toFile());
        assertEquals(2, options.size());
        assertEquals("Folders that hold data the routes read or write, not sources: a change in them does not reload"
                     + " the routes.",
                options.get(0).getDescription());
        assertEquals("advanced", options.get(0).getLabel());
        // one literal is taken as it is, with a + and escaped quotes inside (the description is sanitized after)
        JavaClassSource clazz = (JavaClassSource) Roaster.parse(source.toFile());
        AnnotationSource<?> plain = clazz.getFields().get(1).getAnnotation(Metadata.class);
        assertEquals("A description with a + inside, and \"quotes\"", PrepareCamelJBangMojo.stringValue(plain, "description"));
    }
}
