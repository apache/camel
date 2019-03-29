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
import org.apache.camel.component.as2.api.CanonicalOutputStream;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpException;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.Args;
import org.bouncycastle.cms.CMSCompressedData;
import org.bouncycastle.cms.CMSCompressedDataGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.operator.InputExpanderProvider;
import org.bouncycastle.operator.OutputCompressor;

public class ApplicationPkcs7MimeCompressedDataEntity extends MimeEntity {
    
    private static final String CONTENT_DISPOSITION = "attachment; filename=\"smime.p7z\"";
    
    private byte[] compressedData;
    
    public ApplicationPkcs7MimeCompressedDataEntity(MimeEntity entity2Encrypt,
                                                    CMSCompressedDataGenerator dataGenerator,
                                                    OutputCompressor compressor,
                                                    String compressedContentTransferEncoding,
                                                    boolean isMainBody)
            throws HttpException {
        setContentType(ContentType.create("application/pkcs7-mime", new BasicNameValuePair("smime-type", "compressed-data"),
                new BasicNameValuePair("name", "smime.p7z")));
        setContentTransferEncoding(compressedContentTransferEncoding);
        addHeader(AS2Header.CONTENT_DISPOSITION, CONTENT_DISPOSITION);
        setMainBody(isMainBody);
        try {
            this.compressedData = createCompressedData(entity2Encrypt, dataGenerator, compressor);
        } catch (Exception e) {
            throw new HttpException("Failed to create encrypted data");
        }
    }
    
    public ApplicationPkcs7MimeCompressedDataEntity(byte[] compressedData, String compressedContentTransferEncoding, boolean isMainBody) {
        this.compressedData = Args.notNull(compressedData, "encryptedData");
        
        setContentType(ContentType.create("application/pkcs7-mime", new BasicNameValuePair("smime-type", "compressed-data"),
                new BasicNameValuePair("name", "smime.p7z")));
        setContentTransferEncoding(compressedContentTransferEncoding);
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

            transferEncodedStream.write(compressedData);
        } catch (Exception e) {
            throw new IOException("Failed to write to output stream", e);
        }
    }
    
    public MimeEntity getCompressedEntity(InputExpanderProvider expanderProvider) throws HttpException {
        return EntityParser.parseCompressedEntity(compressedData, expanderProvider);
    }
    
    private byte[] createCompressedData(MimeEntity entity2Encrypt, CMSCompressedDataGenerator compressedDataGenerator, OutputCompressor compressor) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            entity2Encrypt.writeTo(bos);
            bos.flush();

            CMSTypedData contentData = new CMSProcessableByteArray(bos.toByteArray());
            CMSCompressedData  compressedData = compressedDataGenerator.generate(contentData, compressor);
            return compressedData.getEncoded();
        } catch (Exception e) {
            throw new Exception("", e);
        }
    }

}
