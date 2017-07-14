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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.jira.consumer.NewCommentConsumer;
import org.apache.camel.component.jira.consumer.NewIssueConsumer;
import org.apache.camel.component.jira.producer.NewIssueProducer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The jira component interacts with the JIRA issue tracker.
 *
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
@UriEndpoint(firstVersion = "2.15.0", scheme = "jira", title = "JIRA", syntax = "jira:type", label = "api,reporting")
public class JIRAEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = "true")
    private JIRAType type;
    @UriParam @Metadata(required = "true")
    private String serverUrl;
    @UriParam
    private String username;
    @UriParam
    private String password;
    @UriParam(label = "consumer")
    private String jql;
    @UriParam(label = "consumer", defaultValue = "6000")
    private int delay = 6000;

    public JIRAEndpoint(String uri, JIRAComponent component) {
        super(uri, component);
    }

    public Producer createProducer() throws Exception {
        if (type == JIRAType.NEWISSUE) {
            return new NewIssueProducer(this);
        }
        throw new IllegalArgumentException("Producer does not support type: " + type);
    }
    
    public Consumer createConsumer(Processor processor) throws Exception {
        if (type == JIRAType.NEWCOMMENT) {
            return new NewCommentConsumer(this, processor);
        } else if (type == JIRAType.NEWISSUE) {
            return new NewIssueConsumer(this, processor);
        }
        throw new IllegalArgumentException("Consumer does not support type: " + type);
    }

    public boolean isSingleton() {
        return true;
    }

    public JIRAType getType() {
        return type;
    }

    /**
     * Operation to perform such as create a new issue or a new comment
     */
    public void setType(JIRAType type) {
        this.type = type;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * URL to the JIRA server
     */
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Username for login
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password for login
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getJql() {
        return jql;
    }

    /**
     * JQL is the query language from JIRA which allows you to retrieve the data you want.
     * For example <tt>jql=project=MyProject</tt>
     * Where MyProject is the product key in Jira.
     */
    public void setJql(String jql) {
        this.jql = jql;
    }
    
    public int getDelay() {
        return delay;
    }

    /**
     * Delay in seconds when querying JIRA using the consumer.
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }
}
