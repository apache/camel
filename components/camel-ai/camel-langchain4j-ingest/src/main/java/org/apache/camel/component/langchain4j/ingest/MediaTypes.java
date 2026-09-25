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
package org.apache.camel.component.langchain4j.ingest;

import java.util.Map;

import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.VideoContent;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.MimeTypeHelper;

/**
 * The media a {@code modality=media} pipeline can embed: which MIME type a file extension implies, which LangChain4j
 * content type a MIME type belongs to, and the content object handed to the model.
 */
final class MediaTypes {

    /**
     * Camel's MIME table returns the {@code x-} variants for a few audio extensions, which embedding providers do not
     * list; those are rewritten to their registered form.
     */
    private static final Map<String, String> AUDIO_FIXUPS = Map.of(
            "audio/x-wav", "audio/wav",
            "audio/x-flac", "audio/flac",
            "audio/x-aac", "audio/aac",
            "audio/x-aiff", "audio/aiff");

    private MediaTypes() {
    }

    /** The MIME type the document id's file extension implies, per Camel's own MIME table, or null without one. */
    static String fromDocumentId(String documentId) {
        if (FileUtil.onlyExt(documentId, true) == null) {
            // the probe falls back to the whole name, which would type a bare "mp3" id as audio
            return null;
        }
        String type = MimeTypeHelper.probeMimeType(documentId);
        return type == null ? null : AUDIO_FIXUPS.getOrDefault(type, type);
    }

    /** The LangChain4j content type a MIME type belongs to, or null for a type no media pipeline can embed. */
    static ContentType contentTypeOf(String mimeType) {
        if (mimeType.startsWith("audio/")) {
            return ContentType.AUDIO;
        }
        if (mimeType.startsWith("image/")) {
            return ContentType.IMAGE;
        }
        if (mimeType.startsWith("video/")) {
            return ContentType.VIDEO;
        }
        if ("application/pdf".equals(mimeType)) {
            return ContentType.PDF;
        }
        return null;
    }

    /** The content handed to the model: the bytes as base64, typed by the MIME type and its resolved content type. */
    static Content contentOf(ContentType type, String base64, String mimeType) {
        return switch (type) {
            case AUDIO -> AudioContent.from(base64, mimeType);
            case IMAGE -> ImageContent.from(base64, mimeType);
            case VIDEO -> VideoContent.from(base64, mimeType);
            case PDF -> PdfFileContent.from(base64, mimeType);
            case TEXT -> throw new IllegalArgumentException("Not a media type: " + mimeType);
        };
    }
}
