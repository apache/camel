/**
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
package org.apache.camel.component.box;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import com.box.restclientv2.requestsbase.BoxFileUploadRequestObject;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.box.internal.BoxConstants;
import org.apache.camel.component.file.GenericFile;

@Converter
public final class BoxConverter {

    private BoxConverter() {
        //Utility Class
    }

    @Converter
    public static BoxFileUploadRequestObject genericFileToBoxFileUploadRequestObject(GenericFile<?> file, Exchange exchange) throws Exception {
        String folderId = "0";
        if (exchange != null && exchange.getIn() != null) {
            folderId = exchange.getIn().getHeader(BoxConstants.PROPERTY_PREFIX + "folderId", "0", String.class);
        }
        if (file.getFile() instanceof File) {
            // prefer to use a file input stream if its a java.io.File
            File f = (File) file.getFile();
            return BoxFileUploadRequestObject.uploadFileRequestObject(folderId, file.getFileName(), f);
        }
        if (exchange != null) {
            // otherwise ensure the body is loaded as we want the input stream of the body
            file.getBinding().loadContent(exchange, file);
            InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, exchange, file.getBody());
            return BoxFileUploadRequestObject.uploadFileRequestObject(folderId, file.getFileName(), is);
        }
        return null;
    }

    @Converter
    public static BoxFileUploadRequestObject toBox(byte[] data, Exchange exchange) throws Exception {
        String folderId = "0";
        String fileName = "dummy.bin";
        if (exchange != null && exchange.getIn() != null) {
            folderId = exchange.getIn().getHeader(BoxConstants.PROPERTY_PREFIX + "folderId", "0", String.class);
            fileName = exchange.getIn().getHeader("CamelFileName", String.class);
        }
        InputStream is = new ByteArrayInputStream(data);
        return BoxFileUploadRequestObject.uploadFileRequestObject(folderId, fileName, is);
    }
}
