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

import java.io.IOException;

import org.apache.hc.core5.http.ContentType;

/**
 * A generic application entity that wraps EDI payloads with non-standard content types (e.g., text/plain,
 * application/octet-stream). Real-world AS2 partners frequently send EDI payloads wrapped in content types other than
 * the standard application/edifact, application/edi-x12, or application/edi-consent types.
 */
public class GenericApplicationEntity extends ApplicationEntity {

    public GenericApplicationEntity(byte[] content, ContentType contentType, String contentTransferEncoding,
                                    boolean isMainBody, String filename) {
        super(content, contentType, contentTransferEncoding, isMainBody, filename);
    }

    @Override
    public void close() throws IOException {
        // do nothing
    }
}
