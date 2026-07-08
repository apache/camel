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
package org.apache.camel.component.openai;

import java.io.File;
import java.util.Locale;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

/**
 * Resolves the MIME type of file and binary message bodies sent to vision-capable models.
 */
final class MimeTypeHelper {

    /**
     * Content-type headers set by file-based and cloud storage components, checked in order.
     */
    private static final String[] CONTENT_TYPE_HEADERS = {
            "CamelAwsS3ContentType", // AWS S3
            "CamelAzureStorageBlobContentType", // Azure Blob Storage
            "CamelAzureStorageDataLakeContentType", // Azure Data Lake Storage
            "CamelGoogleCloudStorageContentType", // Google Cloud Storage
            "CamelMinioContentType", // Minio (S3-compatible)
            "CamelIBMCOSContentType" // IBM Cloud Object Storage
    };

    private MimeTypeHelper() {
    }

    /**
     * Resolves the MIME type of a local file. Priority: the {@code CamelOpenAIMediaType} header, the
     * {@code CamelFileContentType} header, and finally the file name extension.
     */
    static String resolveForFile(Message in, File file) {
        String mime = headerMimeType(in, OpenAIConstants.MEDIA_TYPE);
        if (mime == null) {
            mime = headerMimeType(in, Exchange.FILE_CONTENT_TYPE);
        }
        if (mime == null) {
            mime = fromFileName(file.getName());
        }
        return mime;
    }

    /**
     * Resolves the MIME type of a binary body (byte[], InputStream or a remote WrappedFile) where no local file is
     * available. Priority: the {@code CamelOpenAIMediaType} header, cloud storage content-type headers,
     * {@code Content-Type}, {@code CamelFileContentType}, and finally the extension of the {@code CamelFileName}
     * header.
     */
    static String resolveForBinary(Message in) {
        String mime = headerMimeType(in, OpenAIConstants.MEDIA_TYPE);
        for (int i = 0; mime == null && i < CONTENT_TYPE_HEADERS.length; i++) {
            mime = headerMimeType(in, CONTENT_TYPE_HEADERS[i]);
        }
        if (mime == null) {
            mime = headerMimeType(in, Exchange.CONTENT_TYPE);
        }
        if (mime == null) {
            mime = headerMimeType(in, Exchange.FILE_CONTENT_TYPE);
        }
        if (mime == null) {
            String fileName = in.getHeader(Exchange.FILE_NAME, String.class);
            if (fileName != null) {
                mime = fromFileName(fileName);
            }
        }
        return mime;
    }

    static boolean isImage(String mime) {
        return mime != null && mime.startsWith("image/");
    }

    static boolean isText(String mime) {
        if (mime == null) {
            return false;
        }
        // XML and JSON are textual formats usable as prompt text, but map to application/* MIME types
        return mime.startsWith("text/") || "application/xml".equals(mime) || "application/json".equals(mime);
    }

    private static String headerMimeType(Message in, String header) {
        String value = in.getHeader(header, String.class);
        if (value == null || value.isBlank()) {
            return null;
        }
        // strip parameters such as "; charset=utf-8"
        int semicolon = value.indexOf(';');
        return semicolon > 0 ? value.substring(0, semicolon).trim() : value.trim();
    }

    private static String fromFileName(String fileName) {
        String mime = org.apache.camel.util.MimeTypeHelper.probeMimeType(fileName);
        if (mime == null) {
            // markdown is not in the camel-util MIME type table
            String name = fileName.toLowerCase(Locale.ROOT);
            if (name.endsWith(".md") || name.endsWith(".markdown")) {
                mime = "text/markdown";
            }
        }
        return mime;
    }
}
