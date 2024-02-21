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
package org.apache.camel.maven.htmlxlsx.process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.camel.maven.htmlxlsx.model.TestResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;

public class XmlToCamelRouteCoverageTest {

    @Test
    public void testXmlToCamelRouteCoverageConverter() {

        // keep jacoco happy
        XmlToCamelRouteCoverageConverter result = new XmlToCamelRouteCoverageConverter();

        assertNotNull(result);
    }

    @Test
    public void testConvert() throws IOException {

        XmlToCamelRouteCoverageConverter converter = new XmlToCamelRouteCoverageConverter();

        TestResult result = converter.convert(loadXml());

        assertAll(
                () -> assertNotNull(result),
                () -> assertNotNull(
                        result.getCamelContextRouteCoverage().getRoutes().getRouteList().get(0).getComponentsMap()));
    }

    @Test
    public void testConvertException() throws JsonProcessingException {

        XmlToCamelRouteCoverageConverter spy = spy(new XmlToCamelRouteCoverageConverter());

        Mockito
                .doAnswer(invocation -> {
                    throw new TestJsonProcessingException();
                })
                .when(spy).readValue(anyString());

        assertThrows(RuntimeException.class, () -> {
            spy.convert(loadXml());
        });
    }

    private String loadXml() throws IOException {

        Path path = Paths.get("src/test/resources/XmlToCamelRouteCoverageConverter.xml");

        return new String(Files.readAllBytes(path));
    }
}
