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
package org.apache.camel.component.jira;

import java.util.regex.Pattern;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.jira.consumer.ConsumerType;
import org.apache.camel.component.jira.consumer.NewCommentConsumer;
import org.apache.camel.component.jira.consumer.NewIssueConsumer;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * The endpoint encapsulates portions of the JIRA API, relying on the jira-rest-java-client SDK.
 * Available endpoint URIs include:
 * 
 * CONSUMERS
 * jira://newIssue (new tickets)
 * jira://newComment (new comments on tickets)
 * 
 * The endpoints will respond with jira-rest-java-client POJOs (Issue, Comment, etc.)
 * 
 * Note: Rather than webhooks, this endpoint relies on simple polling.  Reasons include:
 * - concerned about reliability/stability if this somehow relied on an exposed, embedded server (Jetty?)
 * - the types of payloads we're polling aren't typically large (plus, paging is available in the API)
 * - need to support apps running somewhere not publicly accessible where a webhook would fail
 */
public class JIRAEndpoint extends DefaultEndpoint {

    private String serverUrl;

    private String username;

    private String password;

    private String jql;

    public JIRAEndpoint(String uri, JIRAComponent component) {
        super(uri, component);
    }

    public Producer createProducer() throws Exception {
        return new JIRAProducer(this);
    }
    
    public Consumer createConsumer(Processor processor) throws Exception {
        String uri = getEndpointUri();
        String[] uriSplit = splitUri(getEndpointUri());
        
        if (uriSplit.length > 0) {
            switch (ConsumerType.fromUri(uriSplit[0])) {
            case NEWCOMMENT:
                return new NewCommentConsumer(this, processor);
            case NEWISSUE:
                return new NewIssueConsumer(this, processor);
            default:
                break;
            }
        }

        throw new IllegalArgumentException("Cannot create any consumer with uri " + uri
                + ". A consumer type was not provided (or an incorrect pairing was used).");
    }

    public boolean isSingleton() {
        return true;
    }

    private static String[] splitUri(String uri) {
        Pattern p1 = Pattern.compile("jira:(//)*");
        Pattern p2 = Pattern.compile("\\?.*");

        uri = p1.matcher(uri).replaceAll("");
        uri = p2.matcher(uri).replaceAll("");

        return uri.split("/");
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getJql() {
        return jql;
    }

    public void setJql(String jql) {
        this.jql = jql;
    }
}
