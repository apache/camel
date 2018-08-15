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

/**
 * Secure FTP Component
 */
public class SftpComponent extends RemoteFileComponent<SftpRemoteFile> {

    public SftpComponent() {
        setEndpointClass(SftpEndpoint.class);
    }

    public SftpComponent(CamelContext context) {
        super(context);
        setEndpointClass(SftpEndpoint.class);
    }

    @Override
    protected GenericFileEndpoint<SftpRemoteFile> buildFileEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // get the base uri part before the options as they can be non URI valid such as the expression using $ chars
        // and the URI constructor will regard $ as an illegal character and we dont want to enforce end users to
        // to escape the $ for the expression (file language)
        String baseUri = uri;
        if (uri.contains("?")) {
            baseUri = uri.substring(0, uri.indexOf("?"));
        }

        // lets make sure we create a new configuration as each endpoint can
        // customize its own version
        SftpConfiguration config = new SftpConfiguration(new URI(baseUri));

        FtpUtils.ensureRelativeFtpDirectory(this, config);

        return new SftpEndpoint(uri, this, config);
    }

    protected void afterPropertiesSet(GenericFileEndpoint<SftpRemoteFile> endpoint) throws Exception {
        // noop
    }

}

