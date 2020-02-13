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

import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.http.entity.ContentType;

public final class ContentTypeUtils {

    private ContentTypeUtils() {
    }

    public static boolean isEDIMessageContentType(ContentType ediMessageContentType) {
        switch (ediMessageContentType.getMimeType().toLowerCase()) {
            case AS2MediaType.APPLICATION_EDIFACT:
                return true;
            case AS2MediaType.APPLICATION_EDI_X12:
                return true;
            case AS2MediaType.APPLICATION_EDI_CONSENT:
                return true;
            default:
                return false;
        }
    }

    public static boolean isPkcs7SignatureType(ContentType pcks7SignatureType) {
        switch (pcks7SignatureType.getMimeType().toLowerCase()) {
            case AS2MimeType.APPLICATION_PKCS7_SIGNATURE:
                return true;
            default:
                return false;
        }
    }

}
