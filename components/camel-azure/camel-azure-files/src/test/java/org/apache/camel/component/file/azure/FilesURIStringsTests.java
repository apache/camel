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
package org.apache.camel.component.file.azure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("static-method")
public class FilesURIStringsTests {

    @Test
    void encodeTokenValueShouldEncodeBase64PlusSlashAndPadding() throws Exception {
        // e.g. for the sig base64 param on SAS token the encoding style must encode '+', '/', '='
        assertEquals("%2B%2Fa%3D", FilesURIStrings.encodeTokenValue("+/a="));
    }

    @Test
    void encodeTokenValueShouldPreserveTimeSeparator() throws Exception {
        // e.g. for the se param on SAS token the encoding style must preserve ':'
        assertEquals("11:55:01", FilesURIStrings.encodeTokenValue("11:55:01"));
    }

}
