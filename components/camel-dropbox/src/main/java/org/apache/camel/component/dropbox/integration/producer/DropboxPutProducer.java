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
package org.apache.camel.component.dropbox.integration.producer;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.dropbox.DropboxConfiguration;
import org.apache.camel.component.dropbox.DropboxEndpoint;
import org.apache.camel.component.dropbox.core.DropboxAPIFacade;
import org.apache.camel.component.dropbox.dto.DropboxFileUploadResult;
import org.apache.camel.component.dropbox.util.DropboxHelper;
import org.apache.camel.component.dropbox.util.DropboxResultCode;
import org.apache.camel.component.dropbox.util.DropboxResultHeader;
import org.apache.camel.component.dropbox.util.DropboxUploadMode;
import org.apache.camel.component.dropbox.validator.DropboxConfigurationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DropboxPutProducer extends DropboxProducer {
    private static final transient Logger LOG = LoggerFactory.getLogger(DropboxPutProducer.class);

    public DropboxPutProducer(DropboxEndpoint endpoint, DropboxConfiguration configuration) {
        super(endpoint, configuration);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String remotePath = DropboxHelper.getRemotePath(configuration, exchange);
        String localPath = DropboxHelper.getLocalPath(configuration, exchange);
        DropboxUploadMode uploadMode = DropboxHelper.getUploadMode(configuration, exchange);

        DropboxConfigurationValidator.validatePutOp(localPath, remotePath, uploadMode);

        DropboxFileUploadResult result = new DropboxAPIFacade(configuration.getClient(), exchange)
                .put(localPath, remotePath, uploadMode);

        Map<String, DropboxResultCode> map = result.getResults();
        if (map.size() == 1) {
            for (Map.Entry<String, DropboxResultCode> entry : map.entrySet()) {
                exchange.getIn().setHeader(DropboxResultHeader.UPLOADED_FILE.name(), entry.getKey());
                exchange.getIn().setBody(entry.getValue());
            }

        } else {
            StringBuilder pathsExtracted = new StringBuilder();
            for (Map.Entry<String, DropboxResultCode> entry : map.entrySet()) {
                pathsExtracted.append(entry.getKey()).append("\n");
            }
            exchange.getIn().setHeader(DropboxResultHeader.UPLOADED_FILES.name(), pathsExtracted.toString());
            exchange.getIn().setBody(map);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Uploaded: {}", result.toString());
        }
    }

}
