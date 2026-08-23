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
package org.apache.camel.component.as2.api.util;

import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
import org.apache.camel.component.as2.api.entity.ApplicationEntity;
import org.apache.camel.component.as2.api.entity.ApplicationPkcs7MimeCompressedDataEntity;
import org.apache.hc.core5.http.HttpException;
import org.bouncycastle.cms.CMSCompressedDataGenerator;
import org.bouncycastle.cms.jcajce.ZlibCompressor;
import org.bouncycastle.operator.OutputCompressor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A compressed entity is expanded while the payload is extracted, which happens before the signature has been
 * established. The expansion must therefore be bounded rather than proportional to whatever an unauthenticated sender
 * claims.
 */
class CompressedEntityExpansionBoundTest {

    /** Highly compressible, so the entity on the wire is a tiny fraction of what it expands to. */
    private static final String PAYLOAD = "A".repeat(200_000);

    private static final HttpMessageUtils.DecrpytingAndSigningInfo NO_SECURITY
            = new HttpMessageUtils.DecrpytingAndSigningInfo(null, null);

    @Test
    void expansionBeyondTheBoundIsRefused() throws Exception {
        ApplicationPkcs7MimeCompressedDataEntity compressed = compressedEntity();

        // the failure must come from the bound itself, not from anything downstream, so assert the type
        // the failure must come from the expander refusing, not from anything downstream
        HttpException thrown = assertThrows(HttpException.class,
                () -> HttpMessageUtils.extractEdiPayloadFromCompressedEntity(compressed, NO_SECURITY, false, 1024L),
                "expanding well past the bound must fail rather than allocate");
        assertTrue(thrown.getMessage().contains("decompress"),
                "expected the expander to refuse, but failed with: " + thrown.getMessage());
    }

    @Test
    void expansionWithinTheBoundStillWorks() throws Exception {
        ApplicationPkcs7MimeCompressedDataEntity compressed = compressedEntity();

        ApplicationEntity entity = HttpMessageUtils.extractEdiPayloadFromCompressedEntity(
                compressed, NO_SECURITY, false, 10L * 1024 * 1024);
        assertInstanceOf(ApplicationEntity.class, entity);
    }

    private static ApplicationPkcs7MimeCompressedDataEntity compressedEntity() throws Exception {
        ApplicationEDIFACTEntity ediEntity = new ApplicationEDIFACTEntity(
                PAYLOAD.getBytes(java.nio.charset.StandardCharsets.US_ASCII), "US-ASCII", "7bit", false, null);
        CMSCompressedDataGenerator generator = new CMSCompressedDataGenerator();
        OutputCompressor compressor = new ZlibCompressor();
        return new ApplicationPkcs7MimeCompressedDataEntity(ediEntity, generator, compressor, "base64", false);
    }
}
