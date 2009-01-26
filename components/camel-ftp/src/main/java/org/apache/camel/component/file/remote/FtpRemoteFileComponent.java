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
package org.apache.camel.component.file.remote;

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.commons.net.ftp.FTPFile;

/**
 * Standard FTP Remote File Component
 */
public class FtpRemoteFileComponent extends RemoteFileComponent<FTPFile> {

    public FtpRemoteFileComponent() {
        super();
    }

    public FtpRemoteFileComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected GenericFileEndpoint<FTPFile> buildFileEndpoint(String uri, String remaining, Map parameters) throws Exception {
        // get the uri part before the options as they can be non URI valid such
        // as the expression using $ chars
        if (uri.indexOf("?") != -1) {
            uri = uri.substring(0, uri.indexOf("?"));
        }

        // lets make sure we create a new configuration as each endpoint can
        // customize its own version
        FtpRemoteFileConfiguration config = new FtpRemoteFileConfiguration(new URI(uri));

        FtpRemoteFileOperations operations = new FtpRemoteFileOperations();
        return new FtpRemoteFileEndpoint(uri, this, operations, config);
    }

}

