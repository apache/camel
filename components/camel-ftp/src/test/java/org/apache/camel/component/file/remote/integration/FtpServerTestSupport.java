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
package org.apache.camel.component.file.remote.integration;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.test.infra.ftp.services.FtpServiceFactory;
import org.apache.camel.test.infra.ftp.services.embedded.FtpEmbeddedService;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Base class for unit testing using a FTPServer
 */
public abstract class FtpServerTestSupport extends BaseServerTestSupport {

    @RegisterExtension
    public FtpEmbeddedService service = FtpServiceFactory.createEmbeddedService();

    public void sendFile(String url, Object body, String fileName) {
        template.sendBodyAndHeader(url, body, Exchange.FILE_NAME, new SimpleExpression(fileName));
    }

}
