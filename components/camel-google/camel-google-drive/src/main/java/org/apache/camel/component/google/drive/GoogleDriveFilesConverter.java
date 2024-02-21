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

import java.io.File;
import java.io.InputStream;

import com.google.api.client.http.FileContent;
import com.google.api.client.http.InputStreamContent;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;

@Converter(generateLoader = true)
public final class GoogleDriveFilesConverter {
    private GoogleDriveFilesConverter() {
    }

    @Converter
    public static com.google.api.services.drive.model.File genericFileToGoogleDriveFile(GenericFile<?> file, Exchange exchange)
            throws Exception {
        if (file.getFile() instanceof File) {
            File f = (File) file.getFile();
            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName(f.getName());
            FileContent mediaContent = new FileContent(null, f);
            if (exchange != null) {
                exchange.getIn().setHeader("CamelGoogleDrive.content", fileMetadata);
                exchange.getIn().setHeader("CamelGoogleDrive.mediaContent", mediaContent);
            }
            return fileMetadata;
        }
        if (exchange != null) {
            // body wasn't a java.io.File so let's try to convert it
            file.getBinding().loadContent(exchange, file);
            InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, exchange, file.getBody());

            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName(file.getFileName());
            InputStreamContent mediaContent = new InputStreamContent(null, is);
            exchange.getIn().setHeader("CamelGoogleDrive.content", fileMetadata);
            exchange.getIn().setHeader("CamelGoogleDrive.mediaContent", mediaContent);

            return fileMetadata;
        }
        return null;
    }
}
