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

import org.apache.camel.component.as2.api.AS2SignedDataGenerator;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;

public class MultipartSignedEntity extends MultipartMimeEntity {

    private byte[] signedDataRaw;

    public MultipartSignedEntity(MimeEntity data, AS2SignedDataGenerator signer, String signatureCharSet,
                                 String signatureTransferEncoding, boolean isMainBody, String boundary) throws HttpException {
        super(signer, isMainBody, (boundary == null) ? EntityUtils.createBoundaryValue() : boundary);
        addPart(data);
        ApplicationPkcs7SignatureEntity signature
                = new ApplicationPkcs7SignatureEntity(data, signer, signatureCharSet, signatureTransferEncoding, false);
        addPart(signature);
    }

    protected MultipartSignedEntity(ContentType contentType, String contentTransferEncoding, String boundary,
                                    boolean isMainBody) {
        super(contentType, contentTransferEncoding);
        this.boundary = boundary;
        this.isMainBody = isMainBody;
    }

    /**
     * The first body part exactly as received - its MIME headers and its body in the transfer encoding the sender used
     * - which is what the signature was created over (RFC 1847, 2.1) and what the MIC is computed over (RFC 4130, 7.1).
     * Set by the parser; null for an entity built locally.
     *
     * @return a copy of the bytes, or null
     */
    public byte[] getSignedDataRaw() {
        return signedDataRaw == null ? null : signedDataRaw.clone();
    }

    /** Set by {@link EntityParser} only: the bytes a signature is checked against must not change after parsing. */
    void setSignedDataRaw(byte[] signedDataRaw) {
        this.signedDataRaw = signedDataRaw;
    }

    /**
     * The bytes a signature over this entity has to be verified against: the first body part as received when this
     * entity was parsed, otherwise the first body part serialised. Re-serialising a received part would re-encode it
     * (base64 line length, quoted-printable soft breaks, header formatting) and change the signed bytes.
     *
     * @return the bytes, or null when there is no first body part; a part as received is returned as held, not copied -
     *         to be read, never modified
     */
    public byte[] getSignedDataContent() throws IOException {
        if (signedDataRaw != null) {
            return signedDataRaw;
        }
        MimeEntity signedDataEntity = getSignedDataEntity();
        if (signedDataEntity == null) {
            return null;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            signedDataEntity.writeTo(out);
            return out.toByteArray();
        }
    }

    public MimeEntity getSignedDataEntity() {
        if (getPartCount() > 0) {
            return getPart(0);
        }

        return null;
    }

    public ApplicationPkcs7SignatureEntity getSignatureEntity() {
        if (getPartCount() > 1 && getPart(1) instanceof ApplicationPkcs7SignatureEntity) {
            return (ApplicationPkcs7SignatureEntity) getPart(1);
        }

        return null;
    }

    @Override
    public void close() throws IOException {
        // do nothing
    }
}
