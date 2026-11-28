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
package org.apache.camel.component.fop;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FopEncryptionHeadersTest extends CamelTestSupport {

    @Test
    public void passwordHeaderIsUsedAsGiven() throws Exception {
        context.getPropertiesComponent().addInitialProperty("name", "Camel");
        String password = "{{name}}-2026";

        Map<String, Object> headers = new HashMap<>();
        headers.put("CamelFop.Encrypt.userPassword", password);
        byte[] pdf = render(headers);

        try (PDDocument document = Loader.loadPDF(pdf, password)) {
            assertTrue(document.isEncrypted());
            assertEquals("Test Content", FopHelper.extractTextFrom(document));
        }
        assertThrows(InvalidPasswordException.class, () -> Loader.loadPDF(pdf, "Camel-2026"));
    }

    @Test
    public void permissionHeadersAreApplied() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put("CamelFop.Encrypt.userPassword", "user");
        headers.put("CamelFop.Encrypt.ownerPassword", "owner");
        headers.put("CamelFop.Encrypt.allowPrint", "false");
        headers.put("CamelFop.Encrypt.allowCopyContent", false);
        headers.put("CamelFop.Encrypt.allowEditContent", "true");

        try (PDDocument document = Loader.loadPDF(render(headers), "user")) {
            assertTrue(document.isEncrypted());
            AccessPermission permission = document.getCurrentAccessPermission();
            assertFalse(permission.canPrint());
            assertFalse(permission.canExtractContent());
            assertTrue(permission.canModify());
        }
    }

    private byte[] render(Map<String, Object> headers) {
        return template.requestBodyAndHeaders("fop:pdf", FopHelper.decorateTextWithXSLFO("Test Content"), headers,
                byte[].class);
    }
}
