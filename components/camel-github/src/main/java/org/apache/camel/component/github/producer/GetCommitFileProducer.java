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
package org.apache.camel.component.github.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.github.GitHubConstants;
import org.apache.camel.component.github.GitHubEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.commons.codec.binary.Base64;
import org.eclipse.egit.github.core.Blob;
import org.eclipse.egit.github.core.CommitFile;
import org.eclipse.egit.github.core.service.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer endpoint that gets the file associated with a CommitFile object.
 *
 */
public class GetCommitFileProducer extends AbstractGitHubProducer {
    private static final transient Logger LOG = LoggerFactory.getLogger(GetCommitFileProducer.class);

    private DataService dataService;

    private String encoding = Blob.ENCODING_UTF8;

    public GetCommitFileProducer(GitHubEndpoint endpoint) throws Exception {
        super(endpoint);

        Registry registry = endpoint.getCamelContext().getRegistry();
        Object service = registry.lookupByName(GitHubConstants.GITHUB_DATA_SERVICE);
        if (service != null) {
            LOG.debug("Using DataService found in registry " + service.getClass().getCanonicalName());
            dataService = (DataService) service;
        } else {
            dataService = new DataService();
        }
        initService(dataService);

        if (endpoint.getEncoding() != null) {
            encoding = endpoint.getEncoding();

            if (!encoding.equalsIgnoreCase(Blob.ENCODING_BASE64)
                    && !encoding.equalsIgnoreCase(Blob.ENCODING_UTF8)) {
                throw new IllegalArgumentException("Unknown encoding '" + encoding + "'");
            }
        }
    }

    public void process(Exchange exchange) throws Exception {
        CommitFile file = exchange.getIn().getBody(CommitFile.class);

        Blob response = dataService.getBlob(getRepository(), file.getSha());

        String text = response.getContent();

        // By default, if blob encoding is base64 then we convert to UTF-8. If
        // base64 encoding is required, then must be explicitly requested
        if (response.getEncoding().equals(Blob.ENCODING_BASE64) 
            && encoding != null && encoding.equalsIgnoreCase(Blob.ENCODING_UTF8)) {
            text = new String(Base64.decodeBase64(text));
        }

        // copy the header of in message to the out message
        exchange.getOut().copyFrom(exchange.getIn());
        exchange.getOut().setBody(text);
    }

}
