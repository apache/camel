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

import org.apache.camel.component.as2.api.AS2SignedDataGenerator;
import org.apache.http.HttpException;

public class MultipartSignedEntity extends MultipartMimeEntity {

    public MultipartSignedEntity(MimeEntity data, AS2SignedDataGenerator signer, String signatureCharSet,
                                 String signatureTransferEncoding, boolean isMainBody, String boundary) throws HttpException {
        super(null, isMainBody, boundary);
        setContentType(signer.createMultipartSignedContentType(this.boundary));
        addPart(data);
        ApplicationPkcs7SignatureEntity signature
                = new ApplicationPkcs7SignatureEntity(data, signer, signatureCharSet, signatureTransferEncoding, false);
        addPart(signature);
    }

    protected MultipartSignedEntity(String boundary, boolean isMainBody) {
        this.boundary = boundary;
        this.isMainBody = isMainBody;
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

}
