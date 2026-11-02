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
package org.apache.camel.component.google.drive;

import java.io.IOException;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.services.drive.Drive;

/**
 * Executes the request of a google-drive method, for the producer and the consumer.
 */
final class GoogleDriveRequestHelper {

    private static final String MEDIA = "media";

    private GoogleDriveRequestHelper() {
    }

    /**
     * Executes the request, and gives the content of a file as an InputStream for a media request: drive-files/export,
     * whose response is only the content, and drive-files/get and drive-revisions/get with alt=media. Their execute()
     * downloads the content and returns null (export), or fails to read the content as JSON (get). The stream holds the
     * connection until it is read to the end or closed.
     */
    static Object execute(AbstractGoogleClientRequest<?> request) throws IOException {
        if (request instanceof Drive.Files.Export export) {
            return export.executeMediaAsInputStream();
        }
        if (MEDIA.equals(request.get("alt"))) {
            if (request instanceof Drive.Files.Get get) {
                return get.executeMediaAsInputStream();
            }
            if (request instanceof Drive.Revisions.Get get) {
                return get.executeMediaAsInputStream();
            }
        }
        return request.execute();
    }
}
