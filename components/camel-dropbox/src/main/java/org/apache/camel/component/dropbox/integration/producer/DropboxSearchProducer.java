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
package org.apache.camel.component.dropbox.integration.producer;

import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.SearchMatchV2;
import org.apache.camel.Exchange;
import org.apache.camel.component.dropbox.DropboxConfiguration;
import org.apache.camel.component.dropbox.DropboxEndpoint;
import org.apache.camel.component.dropbox.core.DropboxAPIFacade;
import org.apache.camel.component.dropbox.dto.DropboxSearchResult;
import org.apache.camel.component.dropbox.util.DropboxConstants;
import org.apache.camel.component.dropbox.util.DropboxHelper;
import org.apache.camel.component.dropbox.validator.DropboxConfigurationValidator;

public class DropboxSearchProducer extends DropboxProducer {

    public DropboxSearchProducer(DropboxEndpoint endpoint, DropboxConfiguration configuration) {
        super(endpoint, configuration);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String remotePath = DropboxHelper.getRemotePath(configuration, exchange);
        String query = DropboxHelper.getQuery(configuration, exchange);

        DropboxConfigurationValidator.validateSearchOp(remotePath);

        DropboxSearchResult result = new DropboxAPIFacade(configuration.getClient(), exchange)
                .search(remotePath, query);

        StringBuilder fileExtracted = new StringBuilder();
        for (SearchMatchV2 entry : result.getFound()) {
            Metadata metadataValue = entry.getMetadata().getMetadataValue();
            fileExtracted.append(metadataValue.getName()).append('-').append(metadataValue.getPathDisplay())
                    .append('\n');
        }
        exchange.getIn().setHeader(DropboxConstants.FOUND_FILES, fileExtracted.toString());
        exchange.getIn().setBody(result.getFound());
    }
}
