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
package org.apache.camel.main.download;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaKnownImportsDownloaderTest {

    @Test
    public void testJavaAndGroovyImports() {
        // Java with semicolons, Groovy without, static imports and wildcards (CAMEL-24843)
        List<String> imports = JavaKnownImportsDownloader.determineImports("""
                package com.example;

                import org.apache.commons.validator.routines.EmailValidator;
                import org.apache.commons.lang3.StringUtils
                import static org.apache.commons.text.StringEscapeUtils.escapeJson;
                import static java.time.Duration.ofSeconds
                import com.fasterxml.jackson.databind.*

                def x = 1
                """);
        assertEquals(List.of("org.apache.commons.validator.routines.EmailValidator", "org.apache.commons.lang3.StringUtils",
                "org.apache.commons.text.StringEscapeUtils", "java.time.Duration", "com.fasterxml.jackson.databind"),
                imports);
    }
}
