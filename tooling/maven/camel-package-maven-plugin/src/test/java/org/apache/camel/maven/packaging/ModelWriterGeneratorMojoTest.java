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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that {@link ModelWriterGeneratorMojo#postGenerateWriter(String)} moves the onFallback line below the outputs
 * line, with LF or CRLF line ends (CAMEL-27390).
 */
class ModelWriterGeneratorMojoTest {

    private static final String BEFORE = "        startElement(name);";
    private static final String ON_FALLBACK
            = "        doWriteElement(\"onFallback\", def.getOnFallback(), this::doWriteOnFallbackDefinition);";
    private static final String OUTPUTS
            = "        doWriteList(null, null, def.getOutputs(), this::doWriteProcessorDefinitionRef);";
    private static final String AFTER = "        endElement(name);";
    private static final String EXPECTED = String.join("\n", BEFORE, OUTPUTS, ON_FALLBACK, AFTER);

    private final XmlModelWriterGeneratorMojo mojo = new XmlModelWriterGeneratorMojo(null, null);

    @Test
    void postGenerateWriter_movesOnFallbackBelowOutputsWithLfLineEnds() {
        assertEquals(EXPECTED, mojo.postGenerateWriter(source("\n")));
    }

    @Test
    void postGenerateWriter_givesTheSameResultWithCrLfLineEnds() {
        assertEquals(EXPECTED, mojo.postGenerateWriter(source("\r\n")));
    }

    private static String source(String lineEnd) {
        return BEFORE + lineEnd + ON_FALLBACK + lineEnd + OUTPUTS + lineEnd + AFTER + lineEnd;
    }
}
