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
package org.apache.camel.component.dropbox.integration.consumer;

import com.dropbox.core.v2.files.SearchMatch;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.dropbox.DropboxConfiguration;
import org.apache.camel.component.dropbox.DropboxEndpoint;
import org.apache.camel.component.dropbox.core.DropboxAPIFacade;
import org.apache.camel.component.dropbox.dto.DropboxSearchResult;
import org.apache.camel.component.dropbox.util.DropboxResultHeader;

public class DropboxScheduledPollSearchConsumer extends DropboxScheduledPollConsumer {

    public DropboxScheduledPollSearchConsumer(DropboxEndpoint endpoint, Processor processor, DropboxConfiguration configuration) {
        super(endpoint, processor, configuration);
    }

    /**
     * Poll from a dropbox remote path and put the result in the message exchange
     * @return number of messages polled
     * @throws Exception
     */
    @Override
    protected int poll() throws Exception {
        Exchange exchange = endpoint.createExchange();
        DropboxSearchResult result = new DropboxAPIFacade(configuration.getClient(), exchange)
                .search(configuration.getRemotePath(), configuration.getQuery());

        StringBuilder fileExtracted = new StringBuilder();
        for (SearchMatch entry : result.getFound()) {
            fileExtracted.append(entry.getMetadata().getName()).append("-").append(entry.getMetadata().getPathDisplay()).append("\n");
        }

        exchange.getIn().setHeader(DropboxResultHeader.FOUND_FILES.name(), fileExtracted.toString());
        exchange.getIn().setBody(result.getFound());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Downloaded: {}", result);
        }

        try {
            // send message to next processor in the route
            getProcessor().process(exchange);
            return 1; // number of messages polled
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }
}
