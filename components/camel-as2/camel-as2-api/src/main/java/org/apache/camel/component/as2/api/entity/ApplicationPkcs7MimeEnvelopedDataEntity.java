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
import java.security.PrivateKey;

import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.CanonicalOutputStream;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpException;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.Args;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.operator.OutputEncryptor;

public class ApplicationPkcs7MimeEnvelopedDataEntity extends MimeEntity {
    
    private static final String CONTENT_DISPOSITION = "attachment; filename=\"smime.p7m\"";
    
    private byte[] encryptedData;
    
    public ApplicationPkcs7MimeEnvelopedDataEntity(MimeEntity entity2Encrypt,
                                CMSEnvelopedDataGenerator dataGenerator,
                                OutputEncryptor encryptor,
                                String encryptedContentTransferEncoding,
                                boolean isMainBody)
            throws HttpException {
        setContentType(ContentType.create("application/pkcs7-mime", new BasicNameValuePair("smime-type", "enveloped-data"),
                new BasicNameValuePair("name", "smime.p7m")));
        setContentTransferEncoding(encryptedContentTransferEncoding);
        addHeader(AS2Header.CONTENT_DISPOSITION, CONTENT_DISPOSITION);
        setMainBody(isMainBody);
        try {
            this.encryptedData = createEncryptedData(entity2Encrypt, dataGenerator, encryptor);
        } catch (Exception e) {
            throw new HttpException("Failed to create encrypted data");
        }
    }
    
    public ApplicationPkcs7MimeEnvelopedDataEntity(byte[] encryptedData, String encryptedContentTransferEncoding, boolean isMainBody) {
        this.encryptedData = Args.notNull(encryptedData, "encryptedData");
        
        setContentType(ContentType.create("application/pkcs7-mime", new BasicNameValuePair("smime-type", "enveloped-data"),
                new BasicNameValuePair("name", "smime.p7m")));
        setContentTransferEncoding(encryptedContentTransferEncoding);
        addHeader(AS2Header.CONTENT_DISPOSITION, CONTENT_DISPOSITION);
        setMainBody(isMainBody);
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

            transferEncodedStream.write(encryptedData);
        } catch (Exception e) {
            throw new IOException("Failed to write to output stream", e);
        }
    }
    
    public MimeEntity getEncryptedEntity(PrivateKey privateKey) throws HttpException {
        return EntityParser.parseEnvelopedEntity(encryptedData, privateKey);
    }
    
    private byte[] createEncryptedData(MimeEntity entity2Encrypt, CMSEnvelopedDataGenerator envelopedDataGenerator, OutputEncryptor encryptor) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            entity2Encrypt.writeTo(bos);
            bos.flush();

            CMSTypedData contentData = new CMSProcessableByteArray(bos.toByteArray());
            CMSEnvelopedData  envelopedData = envelopedDataGenerator.generate(contentData, encryptor);
            return envelopedData.getEncoded();
        } catch (Exception e) {
            throw new Exception("", e);
        }
    }

}
