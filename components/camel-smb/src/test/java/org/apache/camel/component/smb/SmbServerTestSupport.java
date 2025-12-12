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
package org.apache.camel.component.smb;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.utils.SmbFiles;
import org.apache.camel.Exchange;
import org.apache.camel.test.infra.smb.services.SmbService;
import org.apache.camel.test.infra.smb.services.SmbServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Base class for unit testing using a SMBServer
 */
public abstract class SmbServerTestSupport extends CamelTestSupport {

    // docker container shared by all tests using this service
    @RegisterExtension
    public static SmbService service = SmbServiceFactory.createSingletonService();

    public byte[] copyFileContentFromContainer(String fileName) {
        return service.copyFileFromContainer(fileName, IOUtils::toByteArray);
    }

    public void sendFile(String url, Object body, String fileName) {
        template.sendBodyAndHeader(url, body, Exchange.FILE_NAME, fileName);
    }

    public void createIfNoDir(String name) throws Exception {
        try (SMBClient smbClient = new SMBClient()) {
            int port = Integer.parseInt(service.address().split(":")[1]);
            try (Connection connection = smbClient.connect("localhost", port)) {
                AuthenticationContext ac
                        = new AuthenticationContext(service.userName(), service.password().toCharArray(), null);
                Session session = connection.authenticate(ac);

                try (DiskShare share = (DiskShare) session.connectShare(service.shareName())) {
                    if (!share.folderExists(name)) {
                        new SmbFiles().mkdirs(share, name);
                    }
                }
            }
        }
    }
}
