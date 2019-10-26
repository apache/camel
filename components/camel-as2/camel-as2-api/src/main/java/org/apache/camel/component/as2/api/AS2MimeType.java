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

public interface AS2MimeType {
    /**
     * Mime Type for Multipart Signed Data
     */
    String MULTIPART_SIGNED = "multipart/signed";
    /**
     * Mime Type for Application PKCS7 Signature
     */
    String APPLICATION_PKCS7_SIGNATURE = "application/pkcs7-signature";
    /**
     * Mime Type for Application PKCS7 Signature
     */
    String APPLICATION_PKCS7_MIME = "application/pkcs7-mime";
    /**
     * Mime Type for Text/Plain Data
     */
    String TEXT_PLAIN = "text/plain";
    /**
     * Mime Type for Application/EDIFACT
     */
    String APPLICATION_EDIFACT = "application/edifact";
    /**
     * Mime Type for Application/EDI-X12
     */
    String APPLICATION_EDI_X12 = "application/edi-x12";
    /**
     * Mime Type for Application/EDI-consent
     */
    String APPLICATION_EDI_CONSENT = "application/edi-consent";
    /**
     * Mime Type for Multipart/Report
     */
    String MULTIPART_REPORT = "multipart/report";
    /**
     * Mime Type for Message/Disposition-Notification
     */
    String MESSAGE_DISPOSITION_NOTIFICATION = "message/disposition-notification";

}
