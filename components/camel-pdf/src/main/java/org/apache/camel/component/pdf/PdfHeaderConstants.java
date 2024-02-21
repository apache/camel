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
package org.apache.camel.component.pdf;

import org.apache.camel.spi.Metadata;

public final class PdfHeaderConstants {
    @Metadata(description = "Expected type is\n" +
                            "https://pdfbox.apache.org/docs/2.0.13/javadocs/org/apache/pdfbox/pdmodel/encryption/ProtectionPolicy.html[ProtectionPolicy].\n"
                            +
                            "If specified then PDF document will be encrypted with it.",
              javaType = "org.apache.pdfbox.pdmodel.encryption.ProtectionPolicy")
    public static final String PROTECTION_POLICY_HEADER_NAME = "protection-policy";
    @Metadata(description = "*Mandatory* header for `append` operation and ignored in all other\n" +
                            "operations. Expected type is\n" +
                            "https://pdfbox.apache.org/docs/2.0.13/javadocs/org/apache/pdfbox/pdmodel/PDDocument.html[PDDocument].\n"
                            +
                            "Stores PDF document which will be used for append operation.",
              javaType = "org.apache.pdfbox.pdmodel.PDDocument")
    public static final String PDF_DOCUMENT_HEADER_NAME = "pdf-document";
    @Metadata(description = "Expected type is\n" +
                            "https://pdfbox.apache.org/docs/2.0.13/javadocs/org/apache/pdfbox/pdmodel/encryption/DecryptionMaterial.html[DecryptionMaterial].\n"
                            +
                            "*Mandatory* header if PDF document is encrypted.",
              javaType = "org.apache.pdfbox.pdmodel.encryption.DecryptionMaterial")
    public static final String DECRYPTION_MATERIAL_HEADER_NAME = "decryption-material";

    private PdfHeaderConstants() {
    }

}
