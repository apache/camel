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
package org.apache.camel.component.as2.api.entity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.CanonicalOutputStream;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpException;
import org.apache.http.entity.ContentType;
import org.apache.http.util.Args;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;

public class ApplicationPkcs7SignatureEntity extends MimeEntity {

    private static final String CONTENT_DISPOSITION = "attachment; filename=\"smime.p7s\"";

    private static final String CONTENT_DESCRIPTION = "S/MIME Cryptographic Signature";

    private byte[] signature;

    public ApplicationPkcs7SignatureEntity(MimeEntity data, CMSSignedDataGenerator signer, String charset, String contentTransferEncoding, boolean isMainBody) throws HttpException {
        Args.notNull(data, "Data");
        Args.notNull(signer, "Signer");

        ContentType contentType = ContentType.parse(EntityUtils.appendParameter(AS2MediaType.APPLICATION_PKCS7_SIGNATURE, "charset",  charset));
        setContentType(contentType.toString());
        setContentTransferEncoding(contentTransferEncoding);
        addHeader(AS2Header.CONTENT_DISPOSITION, CONTENT_DISPOSITION);
        addHeader(AS2Header.CONTENT_DESCRIPTION, CONTENT_DESCRIPTION);
        setMainBody(isMainBody);
        try {
            this.signature = createSignature(data, signer);
        } catch (Exception e) {
            throw new HttpException("Failed to create signed data", e);
        }
    }

    public ApplicationPkcs7SignatureEntity(byte[] signature,
                                           String charset,
                                           String contentTransferEncoding,
                                           boolean isMainBody)
            throws HttpException {
        this.signature = Args.notNull(signature, "signature");
        
        ContentType contentType = ContentType
                .parse(EntityUtils.appendParameter(AS2MediaType.APPLICATION_PKCS7_SIGNATURE, "charset", charset));
        setContentType(contentType.toString());
        setContentTransferEncoding(contentTransferEncoding);
        addHeader(AS2Header.CONTENT_DISPOSITION, CONTENT_DISPOSITION);
        addHeader(AS2Header.CONTENT_DESCRIPTION, CONTENT_DESCRIPTION);
        setMainBody(isMainBody);
    }

    public byte[] getSignature() {
        return signature;
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        NoCloseOutputStream ncos = new NoCloseOutputStream(outstream);

        // Write out mime part headers if this is not the main body of message.
        if (!isMainBody()) {
            try (CanonicalOutputStream canonicalOutstream = new CanonicalOutputStream(ncos, AS2Charset.US_ASCII)) {

                HeaderIterator it = headerIterator();
                while (it.hasNext()) {
                    Header header = it.nextHeader();
                    canonicalOutstream.writeln(header.toString());
                }
                canonicalOutstream.writeln(); // ensure empty line between
                                              // headers and body; RFC2046 -
                                              // 5.1.1
            }
        }

        // Write out signed data.
        String transferEncoding = getContentTransferEncoding() == null ? null : getContentTransferEncoding().getValue();
        try (OutputStream transferEncodedStream = EntityUtils.encode(ncos, transferEncoding)) {

            transferEncodedStream.write(signature);
        } catch (Exception e) {
            throw new IOException("Failed to write to output stream", e);
        }
    }

    private byte[] createSignature(MimeEntity data, CMSSignedDataGenerator signer) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            data.writeTo(bos);
            bos.flush();

            CMSTypedData contentData = new CMSProcessableByteArray(bos.toByteArray());
            CMSSignedData  signedData = signer.generate(contentData, false);
            return signedData.getEncoded();
        } catch (Exception e) {
            throw new Exception("", e);
        }

    }

}
