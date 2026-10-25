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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.component.github2.GitHub2Endpoint;
import org.apache.camel.util.ObjectHelper;
import org.kohsuke.github.GHContent;

/**
 * Producer endpoint that gets the content of a file at a commit. The "CamelGitHubCommitSha" header is the commit id (40
 * hexadecimal characters), and the "CamelGitHubFilePath" header, or else the message body, is the path of the file in
 * the repository. The file is read from the tree of that commit, and the body is set to its content as text.
 */
public class GetCommitFileProducer extends AbstractGitHub2Producer {

    private static final Pattern COMMIT_ID = Pattern.compile("[0-9a-fA-F]{40}");

    private final String encoding;

    public GetCommitFileProducer(GitHub2Endpoint endpoint) {
        super(endpoint);
        this.encoding = endpoint.getEncoding();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String sha = exchange.getIn().getHeader(GitHub2Constants.GITHUB_COMMIT_SHA, String.class);
        if (ObjectHelper.isEmpty(sha)) {
            throw new IllegalArgumentException(
                    "The getCommitFile operation needs the " + GitHub2Constants.GITHUB_COMMIT_SHA
                                               + " header with the commit id");
        }
        if (!COMMIT_ID.matcher(sha).matches()) {
            throw new IllegalArgumentException(
                    "The " + GitHub2Constants.GITHUB_COMMIT_SHA
                                               + " header must be a commit id of 40 hexadecimal characters, but was: "
                                               + sha);
        }

        String path = exchange.getIn().getHeader(GitHub2Constants.GITHUB_FILE_PATH, String.class);
        if (ObjectHelper.isEmpty(path)) {
            path = exchange.getIn().getBody(String.class);
        }
        if (ObjectHelper.isEmpty(path)) {
            throw new InvalidPayloadException(exchange, String.class, exchange.getIn());
        }

        for (String segment : path.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(
                        "The path of the file must be a path in the tree of the commit, without . or .. segments, but was: "
                                                   + path);
            }
        }

        // the file at its path in the tree of the commit
        GHContent file = getRepository().getFileContent(path, sha);
        if (!file.isFile()) {
            throw new IllegalArgumentException(
                    "The path " + path + " at commit " + sha + " is not a file but a " + file.getType());
        }

        Charset charset = encoding != null ? Charset.forName(encoding) : Charset.defaultCharset();
        String content = new String(readContent(file), charset);

        // copy the header of in message to the out message
        exchange.getMessage().copyFrom(exchange.getIn());
        exchange.getMessage().setBody(content);
    }

    @SuppressWarnings("deprecation")
    private byte[] readContent(GHContent file) throws IOException {
        if ("base64".equals(file.getEncoding())) {
            // GitHub wraps the lines of the base64 content
            return Base64.getMimeDecoder().decode(file.getEncodedContent());
        }
        // the contents API gives no content for a file of more than 1 MB: read its blob
        try (InputStream is = getRepository().getBlob(file.getSha()).read()) {
            return is.readAllBytes();
        }
    }
}
