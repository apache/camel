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

import com.box.boxjavalibv2.exceptions.BoxJSONException;
import com.box.restclientv2.exceptions.BoxRestException;
import com.box.restclientv2.requestsbase.BoxFileUploadRequestObject;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.component.box.internal.BoxConstants;
import org.apache.camel.component.file.GenericFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Converter
public final class BoxConverter {

    private BoxConverter() {
        //Utility Class
    }

    @Converter
    public static BoxFileUploadRequestObject genericFileToBoxFileUploadRequestObject(GenericFile<?> file, Exchange exchange) throws IOException, NoTypeConversionAvailableException, BoxRestException, BoxJSONException {
        String parentId = "0";
        if (exchange != null) {
            parentId = exchange.getProperty(BoxConstants.PROPERTY_PREFIX + "parentId", "0", String.class);
        }
        if (file.getFile() instanceof File) {
            // prefer to use a file input stream if its a java.io.File
            File f = (File) file.getFile();
            return BoxFileUploadRequestObject.uploadFileRequestObject(parentId, file.getFileName(), f);
        }
        if (exchange != null) {
            // otherwise ensure the body is loaded as we want the input stream of the body
            file.getBinding().loadContent(exchange, file);
            InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, exchange, file.getBody());
            return BoxFileUploadRequestObject.uploadFileRequestObject(parentId, file.getFileName(), is);
        }
        return null;
    }
}
