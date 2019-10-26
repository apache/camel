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
package org.apache.camel.component.as2.api;

public interface AS2MediaType {

    /**
     * Media Type for Multipart Signed Data
     */
    String MULTIPART_SIGNED = "multipart/signed; protocol=\"application/pkcs7-signature\"";
    /**
     * Media Type for Application PKCS7 Signature
     */
    String APPLICATION_PKCS7_SIGNATURE = "application/pkcs7-signature; name=smime.p7s; smime-type=signed-data";
    /**
     * Media Type for Application PKCS7 Enveloped Data
     */
    String APPLICATION_PKCS7_MIME_ENVELOPED = "application/pkcs7-mime; smime-type=enveloped-data; name=smime.p7m";
    /**
     * Media Type for Application PKCS7 Compressed Data
     */
    String APPLICATION_PKCS7_MIME_COMPRESSED = "application/pkcs7-mime; smime-type=compressed-data; name=smime.p7z";
    /**
     * Media Type for Text/Plain Data
     */
    String TEXT_PLAIN = "text/plain";
    /**
     * Media Type for Application/EDIFACT
     */
    String APPLICATION_EDIFACT = "application/edifact";
    /**
     * Media Type for Application/EDI-X12
     */
    String APPLICATION_EDI_X12 = "application/edi-x12";
    /**
     * Media Type for Application/EDI-consent
     */
    String APPLICATION_EDI_CONSENT = "application/edi-consent";
}
