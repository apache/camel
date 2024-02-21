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

import org.apache.camel.maven.htmlxlsx.TestUtil;
import org.apache.camel.maven.htmlxlsx.model.TestResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;

public class TestResultParserTest {

    @Test
    public void testTestResultParser() {

        // keep jacoco happy
        TestResultParser result = new TestResultParser();

        assertNotNull(result);
    }

    @Test
    public void testParse() {

        TestResultParser parser = new TestResultParser();

        TestResult result = parser.parse(TestUtil.testResult());

        assertAll(
                () -> assertNotNull(result),
                () -> assertNotNull(result.getCamelContextRouteCoverage().getRoutes().getRouteList().get(0).getComponents()));
    }

    @Test
    public void testParseException() {

        TestResultParser spy = spy(new TestResultParser());

        Mockito
                .doAnswer(invocation -> {
                    throw new TestJsonProcessingException();
                })
                .when(spy).objectMapper();

        assertThrows(RuntimeException.class, () -> {
            spy.parse(TestUtil.testResult());
        });
    }
}
