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

import java.io.File;
import java.util.Comparator;
import java.util.List;

import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.JBangModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrepareCamelJBangMojoTest {

    // a configuration class as in camel-jbang-run, read from its compiled form
    static final class Constants {

        @Metadata(description = "Folders that hold data the routes read or write,"
                                + " not sources: a change in them does not reload the routes.",
                  javaType = "String", label = "advanced")
        public static final String DATA_DIR = "camel.jbang.dataDir";

        @Metadata(description = "Whether to run with the console", javaType = "boolean", defaultValue = "true")
        public static final String CONSOLE = "camel.jbang.console";

        @Metadata(description = "The token", javaType = "String", security = "secret")
        public static final String TOKEN = "camel.jbang.token";

        @Deprecated
        @Metadata(description = "The runtime", javaType = "String", enums = "main,spring-boot,quarkus")
        public static final String RUNTIME = "camel.jbang.runtime";

        // not an option
        public static final String OTHER = "camel.jbang.other";

        private Constants() {
        }
    }

    @Test
    void theOptionsOfACompiledConfigurationClass() {
        List<JBangModel.JBangOptionModel> options = PrepareCamelJBangMojo.parseConfigurationClass(Constants.class)
                .stream().sorted(Comparator.comparing(JBangModel.JBangOptionModel::getName)).toList();

        assertEquals(4, options.size());
        JBangModel.JBangOptionModel console = options.get(0);
        assertEquals("camel.jbang.console", console.getName());
        assertEquals("boolean", console.getType());
        assertEquals(Boolean.TRUE, console.getDefaultValue());
        assertNull(console.getLabel());

        JBangModel.JBangOptionModel dataDir = options.get(1);
        assertEquals("camel.jbang.dataDir", dataDir.getName());
        // the compiler joined the literals: one text, no +
        assertEquals("Folders that hold data the routes read or write, not sources: a change in them does not reload"
                     + " the routes.",
                dataDir.getDescription());
        assertEquals("advanced", dataDir.getLabel());
        assertFalse(dataDir.isSecret());

        JBangModel.JBangOptionModel runtime = options.get(2);
        assertTrue(runtime.isDeprecated());
        assertEquals(List.of("main", "spring-boot", "quarkus"), runtime.getEnums());

        JBangModel.JBangOptionModel token = options.get(3);
        assertTrue(token.isSecret());
        assertEquals("secret", token.getSecurity());
    }

    @Test
    void theClassOfASourceFile() {
        File file = new File(
                "../../dsl/camel-jbang/camel-jbang-run/src/main/java/org/apache/camel/dsl/jbang/run/common/"
                             + "CamelJBangConstants.java");
        assertEquals("org.apache.camel.dsl.jbang.run.common.CamelJBangConstants", PrepareCamelJBangMojo.className(file));
    }

    @Test
    void aSourceFileNotUnderSrcMainJava() {
        File file = new File("somewhere/org/apache/camel/CamelJBangConstants.java");
        assertThrows(IllegalArgumentException.class, () -> PrepareCamelJBangMojo.className(file));
    }
}
