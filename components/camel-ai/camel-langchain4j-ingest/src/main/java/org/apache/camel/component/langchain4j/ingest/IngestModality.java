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

/**
 * What the message body is, and therefore how it is embedded.
 */
public enum IngestModality {

    /** Text: read as a String, split into segments and embedded segment by segment. */
    TEXT,

    /**
     * Media: audio, an image, video or a PDF, read as bytes and embedded whole, as one vector, by an embedding model
     * that declares the matching {@code ContentType} among its supported content types. The medium is told by the MIME
     * type, from the {@code contentType} option or the document id's file extension.
     */
    MEDIA
}
