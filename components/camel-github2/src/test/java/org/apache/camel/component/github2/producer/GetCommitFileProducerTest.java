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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHBlob;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The getCommitFile producer reads the file at its path in the tree of the commit.
 */
class GetCommitFileProducerTest extends CamelTestSupport {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
    private static final String PATH = "docs/readme.md";
    // longer than the 76 characters of a line of MIME base64, so the encoded content has line breaks
    private static final String TEXT = "The Open Source Integration Framework, with more than 300 connectors: æøå\n";

    private final GHRepository repository = mock(GHRepository.class);

    @BindToRegistry(GitHub2Constants.GITHUB_CLIENT)
    private final GitHub github = createGitHub();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("github2:getCommitFile?repoOwner=acme&repoName=demo&oauthToken=not-used&encoding=UTF-8");
            }
        };
    }

    @Test
    void fileFromPathHeader() throws Exception {
        GHContent content
                = fileContent("base64", Base64.getMimeEncoder().encodeToString(TEXT.getBytes(StandardCharsets.UTF_8)));
        when(repository.getFileContent(PATH, SHA)).thenReturn(content);

        String body = template.requestBodyAndHeaders("direct:start", "ignored",
                Map.of(GitHub2Constants.GITHUB_COMMIT_SHA, SHA, GitHub2Constants.GITHUB_FILE_PATH, PATH), String.class);

        assertEquals(TEXT, body);
    }

    @Test
    void fileFromPathInBody() throws Exception {
        GHContent content
                = fileContent("base64", Base64.getMimeEncoder().encodeToString(TEXT.getBytes(StandardCharsets.UTF_8)));
        when(repository.getFileContent(PATH, SHA)).thenReturn(content);

        String body = template.requestBodyAndHeader("direct:start", PATH, GitHub2Constants.GITHUB_COMMIT_SHA, SHA,
                String.class);

        assertEquals(TEXT, body);
    }

    @Test
    void largeFileIsReadAsBlob() throws Exception {
        // the contents API gives no content for a file of more than 1 MB
        GHContent content = fileContent("none", "");
        when(content.getSha()).thenReturn("fedcba9876543210fedcba9876543210fedcba98");
        when(repository.getFileContent(PATH, SHA)).thenReturn(content);
        GHBlob blob = mock(GHBlob.class);
        when(blob.read()).thenReturn(new ByteArrayInputStream(TEXT.getBytes(StandardCharsets.UTF_8)));
        when(repository.getBlob("fedcba9876543210fedcba9876543210fedcba98")).thenReturn(blob);

        String body = template.requestBodyAndHeader("direct:start", PATH, GitHub2Constants.GITHUB_COMMIT_SHA, SHA,
                String.class);

        assertEquals(TEXT, body);
    }

    @Test
    void shaMustBeCommitId() throws Exception {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader("direct:start", PATH, GitHub2Constants.GITHUB_COMMIT_SHA, "main"));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains("40 hexadecimal characters"), cause.getMessage());
        verify(repository, never()).getFileContent(anyString(), anyString());
    }

    @Test
    void pathMustBeInTreeOfCommit() throws Exception {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader("direct:start", "docs/../readme.md", GitHub2Constants.GITHUB_COMMIT_SHA,
                        SHA));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains("path in the tree of the commit"), cause.getMessage());
        verify(repository, never()).getFileContent(anyString(), anyString());
    }

    @Test
    void shaHeaderIsRequired() throws Exception {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:start", SHA));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains(GitHub2Constants.GITHUB_COMMIT_SHA), cause.getMessage());
        verify(repository, never()).getFileContent(anyString(), anyString());
    }

    private static GHContent fileContent(String encoding, String encodedContent) throws IOException {
        GHContent content = mock(GHContent.class);
        when(content.isFile()).thenReturn(true);
        when(content.getType()).thenReturn("file");
        when(content.getEncoding()).thenReturn(encoding);
        when(content.getEncodedContent()).thenReturn(encodedContent);
        return content;
    }

    private GitHub createGitHub() {
        GitHub client = mock(GitHub.class);
        try {
            when(client.getRepository("acme/demo")).thenReturn(repository);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return client;
    }
}
