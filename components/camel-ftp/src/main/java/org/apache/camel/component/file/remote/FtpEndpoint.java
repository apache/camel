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

import org.apache.camel.Processor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FtpEndpoint extends RemoteFileEndpoint<RemoteFileExchange> {
    private static final transient Log LOG = LogFactory.getLog(FtpEndpoint.class);

    public FtpEndpoint(String uri, RemoteFileComponent remoteFileComponent, RemoteFileConfiguration configuration) {
        super(uri, remoteFileComponent, configuration);
    }

    public FtpEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public FtpProducer createProducer() throws Exception {
        return new FtpProducer(this, FtpUtils.createNewFtpClient());
    }

    public FtpConsumer createConsumer(Processor processor) throws Exception {
        final FtpConsumer consumer = new FtpConsumer(this, processor, FtpUtils.createNewFtpClient());
        configureConsumer(consumer);
        return consumer;
    }

}
