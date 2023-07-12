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
package org.apache.camel.component.jira.producer;

import java.io.File;
import java.io.InputStream;
import java.net.URI;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.jira.JiraEndpoint;
import org.apache.camel.support.DefaultProducer;

import static org.apache.camel.component.jira.JiraConstants.ISSUE_KEY;

public class AttachFileProducer extends DefaultProducer {
    public AttachFileProducer(JiraEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws InvalidPayloadException {
        String issueKey = exchange.getIn().getHeader(ISSUE_KEY, String.class);
        if (issueKey == null) {
            throw new IllegalArgumentException(
                    "Missing exchange input header named 'IssueKey', it should specify the issue key to attach a file.");
        }

        // check for java.io.File first before using input stream for file content
        InputStream is = null;
        String name = null;
        File file = null;
        Object body = exchange.getIn().getBody();
        if (body instanceof File) {
            file = (File) body;
        } else {
            WrappedFile<?> wf = exchange.getIn().getBody(WrappedFile.class);
            if (wf != null && wf.getFile() instanceof File) {
                file = (File) wf.getFile();
            }
        }
        if (file == null) {
            is = exchange.getIn().getMandatoryBody(InputStream.class);
            name = exchange.getIn().getHeader(Exchange.FILE_NAME, exchange.getMessage().getMessageId(), String.class);
        }

        JiraRestClient client = ((JiraEndpoint) getEndpoint()).getClient();
        IssueRestClient issueClient = client.getIssueClient();
        Issue issue = issueClient.getIssue(issueKey).claim();
        URI attachmentsUri = issue.getAttachmentsUri();
        if (file != null) {
            issueClient.addAttachments(attachmentsUri, file);
        } else {
            issueClient.addAttachment(attachmentsUri, is, name);
        }
    }
}
