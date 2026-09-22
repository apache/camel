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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24852: a resource:classpath: or resource:file: reference in an expression, checked against the files next to
 * the route, where camel run looks them up.
 */
class SourceValidatorResourceRefsTest {

    @TempDir
    Path dir;

    private static final String ROUTE = """
            - route:
                from:
                  uri: timer:tick
                  steps:
                    - setBody:
                        expression:
                          groovy:
                            expression: "resource:%s"
                    - to:
                        uri: log:done
            """;

    @Test
    void aClasspathReferenceToAScriptNextToTheRouteIsFine() throws Exception {
        // camel run looks a classpath: resource up next to the route files (DependencyDownloaderResourceLoader)
        Files.writeString(dir.resolve("shipment-mapping.groovy"), "body");
        assertThat(SourceValidator.validateResourceRefs(ROUTE.formatted("classpath:shipment-mapping.groovy"), dir)).isEmpty();
    }

    @Test
    void aFileReferenceToAScriptNextToTheRouteIsFine() throws Exception {
        Files.writeString(dir.resolve("shipment-mapping.groovy"), "body");
        assertThat(SourceValidator.validateResourceRefs(ROUTE.formatted("file:shipment-mapping.groovy"), dir)).isEmpty();
    }

    @Test
    void aClasspathReferenceToAStylesheetNextToTheRouteIsFine() throws Exception {
        // an .xsl is not a source: camel run puts it on the classpath
        Files.writeString(dir.resolve("packing-slip.xsl"), "<xsl/>");
        assertThat(SourceValidator.validateResourceRefs(ROUTE.formatted("classpath:packing-slip.xsl"), dir)).isEmpty();
    }

    @Test
    void aReferenceToAFileThatIsNotThereSaysSo() throws Exception {
        List<String> errors = SourceValidator.validateResourceRefs(ROUTE.formatted("file:mapping/shipment.groovy"), dir);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0))
                .startsWith("Line 8: resource:file:mapping/shipment.groovy: the file does not exist in the directory")
                .contains("add the file next to the route files");

        Files.writeString(dir.resolve("shipment.groovy"), "body");
        errors = SourceValidator.validateResourceRefs(ROUTE.formatted("file:mapping/shipment.groovy"), dir);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("the directory has shipment.groovy: write resource:file:shipment.groovy");
    }
}
