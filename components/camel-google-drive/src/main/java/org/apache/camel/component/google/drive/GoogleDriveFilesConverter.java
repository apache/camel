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
import java.io.IOException;
import java.io.InputStream;

import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(generateLoader = true)
public final class GoogleDriveFilesConverter {
    private static final Logger LOG = LoggerFactory.getLogger(GoogleDriveFilesConverter.class);
    
    private GoogleDriveFilesConverter() {
    }
    
    @Converter
    public static com.google.api.services.drive.model.File genericFileToGoogleDriveFile(GenericFile<?> file, Exchange exchange) throws Exception {       
        if (file.getFile() instanceof File) {
            File f = (File) file.getFile();
            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setTitle(f.getName());
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
            fileMetadata.setTitle(file.getFileName());
            InputStreamContent mediaContent = new InputStreamContent(null, is);
            exchange.getIn().setHeader("CamelGoogleDrive.content", fileMetadata);
            exchange.getIn().setHeader("CamelGoogleDrive.mediaContent", mediaContent);
            
            return fileMetadata;
        }
        return null;
    }
    
    // convenience method that takes google file metadata and converts that to an inputstream
    @Converter
    public static InputStream download(com.google.api.services.drive.model.File fileMetadata, Exchange exchange) throws Exception {
        if (fileMetadata.getDownloadUrl() != null && fileMetadata.getDownloadUrl().length() > 0) {
            try {
                // TODO maybe separate this out as custom drive API ex. google-drive://download...
                HttpResponse resp = getClient(exchange).getRequestFactory().buildGetRequest(new GenericUrl(fileMetadata.getDownloadUrl())).execute();
                return resp.getContent();
            } catch (IOException e) {
                LOG.debug("Could not download file.", e);
                return null;
            }
        } else {
            // The file doesn't have any content stored on Drive.
            return null;
        }
    }

    @Converter
    public static String downloadContentAsString(com.google.api.services.drive.model.File fileMetadata, Exchange exchange) throws Exception {
        InputStream is = download(fileMetadata, exchange);
        if (is != null) {
            return exchange.getContext().getTypeConverter().convertTo(String.class, exchange, is);
        }
        return null;
    }
    
    @Converter
    public static com.google.api.services.drive.model.ChildReference genericStringToChildReference(String payload, Exchange exchange) throws Exception {       
        if (payload != null) {
            com.google.api.services.drive.model.ChildReference childReference = new com.google.api.services.drive.model.ChildReference();
            childReference.setId(payload);
            return childReference;
        }
        return null;
    }
    
    private static Drive getClient(Exchange exchange) {
        GoogleDriveComponent component = exchange.getContext().getComponent("google-drive", GoogleDriveComponent.class);
        return component.getClient(component.getConfiguration());
    }
}
