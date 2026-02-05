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
package org.apache.camel.component.github2.producer;

import java.nio.charset.Charset;
import java.util.Base64;

import org.apache.camel.Exchange;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.component.github2.GitHub2Endpoint;
import org.apache.camel.util.ObjectHelper;
import org.kohsuke.github.GHBlob;
import org.kohsuke.github.GHCommit;

/**
 * Producer endpoint that gets a file from a commit. The endpoint requires the "CamelGitHubCommitSha" header,
 * identifying the commit SHA. The message body should contain the file path within the repository.
 */
public class GetCommitFileProducer extends AbstractGitHub2Producer {

    private final String encoding;

    public GetCommitFileProducer(GitHub2Endpoint endpoint) {
        super(endpoint);
        this.encoding = endpoint.getEncoding();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String sha = exchange.getIn().getHeader(GitHub2Constants.GITHUB_COMMIT_SHA, String.class);
        if (ObjectHelper.isEmpty(sha)) {
            sha = exchange.getIn().getBody(String.class);
        }

        if (ObjectHelper.isEmpty(sha)) {
            throw new IllegalArgumentException("Commit SHA must be specified");
        }

        GHCommit commit = getRepository().getCommit(sha);
        GHBlob blob = null;

        // Get the tree and find the file
        // For simplicity, we get the blob SHA from the exchange body if it's a file path
        String filePath = exchange.getIn().getBody(String.class);
        if (filePath != null && !filePath.equals(sha)) {
            // Try to get the file content from the commit
            blob = getRepository().getBlob(sha);
        } else {
            blob = getRepository().getBlob(sha);
        }

        String content = null;
        if (blob != null) {
            String encodedContent = blob.getContent();
            Charset charset = encoding != null ? Charset.forName(encoding) : Charset.defaultCharset();
            content = new String(Base64.getDecoder().decode(encodedContent), charset);
        }

        // copy the header of in message to the out message
        exchange.getMessage().copyFrom(exchange.getIn());
        exchange.getMessage().setBody(content);
    }
}
