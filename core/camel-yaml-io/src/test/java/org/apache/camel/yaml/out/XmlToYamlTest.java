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
package org.apache.camel.yaml.out;

import java.io.FileInputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.xml.in.ModelParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmlToYamlTest {

    public static final String NAMESPACE = "http://camel.apache.org/schema/spring";

    private static final Logger LOG = LoggerFactory.getLogger(XmlToYamlTest.class);

    @ParameterizedTest
    @MethodSource("routes")
    @DisplayName("Test xml to yaml for <routes>")
    void testRoutes(String xml) throws Exception {
        try (InputStream is = new FileInputStream("../camel-xml-io/src/test/resources/" + xml)) {
            RoutesDefinition expected = new ModelParser(is, NAMESPACE).parseRoutesDefinition().get();
            StringWriter sw = new StringWriter();
            new ModelWriter(sw).writeRoutesDefinition(expected);
            LOG.info("xml={}\n{}\n", xml, sw);
        }
    }

    private static Stream<Arguments> routes() {
        return definitions("routes");
    }

    private static Stream<Arguments> definitions(String xml) {
        try {
            return Files.list(Paths.get("../camel-xml-io/src/test/resources"))
                    .filter(p -> {
                        try {
                            return Files.isRegularFile(p)
                                    && p.getFileName().toString().endsWith(".xml")
                                    && Files.readString(p).contains("<" + xml);
                        } catch (IOException e) {
                            throw new IOError(e);
                        }
                    })
                    .map(p -> p.getFileName().toString())
                    .flatMap(p -> Stream.of(Arguments.of(p, NAMESPACE)));
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

}
