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
package org.apache.camel.component.file.azure;

import java.util.Map;

import com.azure.storage.file.share.models.ShareFileItem;
import org.apache.camel.CamelContext;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.component.file.remote.RemoteFileComponent;
import org.apache.camel.spi.annotations.Component;

/**
 * Camel Azure Files component.
 */
@Component(FilesComponent.SCHEME)
public class FilesComponent extends RemoteFileComponent<ShareFileItem> {

    public static final String SCHEME = "azure-files";

    public FilesComponent() {
    }

    public FilesComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected FilesEndpoint buildFileEndpoint(
            String uri, String remaining,
            Map<String, Object> parameters)
            throws Exception {
        var config = new FilesConfiguration(FilesURIStrings.getBaseURI(uri));
        return new FilesEndpoint(uri, this, config);
    }

    @Override
    protected void afterPropertiesSet(GenericFileEndpoint<ShareFileItem> endpoint) throws Exception {
        if (endpoint.getFileExist() == GenericFileExist.Append) {
            throw new IllegalArgumentException(
                    "Appending to remote files is not supported.");
        } else if (endpoint.getFileExist() == GenericFileExist.Move) {
            throw new IllegalArgumentException(
                    "Moving of existing remote files is not implemented.");
        }
    }
}
