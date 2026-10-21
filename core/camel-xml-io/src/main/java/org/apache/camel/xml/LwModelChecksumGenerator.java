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
package org.apache.camel.xml;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.spi.ModelChecksumGenerator;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.xml.out.ModelWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ModelChecksumGenerator} based on the generated {@link ModelWriter}, which writes every option of the model:
 * the SHA-256 of the definition as XML, with property placeholders as written, without the ids Camel generates and
 * without source locations (also while debugging). The first 12 hex characters of it are the checksum.
 */
@JdkService(ModelChecksumGenerator.FACTORY)
public class LwModelChecksumGenerator implements ModelChecksumGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(LwModelChecksumGenerator.class);

    /** The bytes of the digest the checksum has: 12 hex characters. */
    private static final int CHECKSUM_BYTES = 6;

    @Override
    public String generateChecksum(CamelContext context, NamedNode definition) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (Writer out = new OutputStreamWriter(
                    new DigestOutputStream(OutputStream.nullOutputStream(), digest), StandardCharsets.UTF_8)) {
                LwModelToXMLDumper.write(context, definition, out, false, false, false);
            }
            return HexFormat.of().formatHex(digest.digest(), 0, CHECKSUM_BYTES);
        } catch (Exception e) {
            LOG.debug("Cannot compute the checksum of {} due to {}", definition, e.getMessage(), e);
            return null;
        }
    }
}
