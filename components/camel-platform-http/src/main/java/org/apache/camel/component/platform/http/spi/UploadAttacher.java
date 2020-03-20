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
package org.apache.camel.component.platform.http.spi;

import java.io.File;

import org.apache.camel.Message;

/**
 * Attaches file uploads to Camel {@link Message}s.
 */
public interface UploadAttacher {

    /**
     * Attach the uploaded file represented by the given {@code localFile} and {@code fileName} to the given
     * {@code message}
     *
     * @param localFile the uploaded file stored locally
     * @param fileName  the name of the upload as sent by the client
     * @param message   the {@link Message} to attach the upload to
     */
    void attachUpload(File localFile, String fileName, Message message);

}
