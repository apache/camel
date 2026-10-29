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
package org.apache.camel.component.mustache;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With contentCache=false a changed template is read again.
 */
public class MustacheContentCacheTest extends CamelTestSupport {

    @TempDir
    Path testDirectory;

    @Test
    public void testContentCacheDisabledReadsChangedTemplate() throws Exception {
        Path templateFile = testDirectory.resolve("reload.mustache");
        Files.writeString(templateFile, "v1 {{body}}");
        String uri = "mustache:file:" + templateFile.toAbsolutePath() + "?contentCache=false";

        assertEquals("v1 one", template.requestBody(uri, "one", String.class));

        Files.writeString(templateFile, "v2 {{body}}");

        assertEquals("v2 two", template.requestBody(uri, "two", String.class));
    }
}
