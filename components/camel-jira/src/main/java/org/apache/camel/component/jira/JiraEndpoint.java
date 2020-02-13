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
package org.apache.camel.component.jira;

import java.net.URI;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.jira.consumer.NewCommentsConsumer;
import org.apache.camel.component.jira.consumer.NewIssuesConsumer;
import org.apache.camel.component.jira.oauth.JiraOAuthAuthenticationHandler;
import org.apache.camel.component.jira.oauth.OAuthAsynchronousJiraRestClientFactory;
import org.apache.camel.component.jira.producer.AddCommentProducer;
import org.apache.camel.component.jira.producer.AddIssueLinkProducer;
import org.apache.camel.component.jira.producer.AddIssueProducer;
import org.apache.camel.component.jira.producer.AddWorkLogProducer;
import org.apache.camel.component.jira.producer.AttachFileProducer;
import org.apache.camel.component.jira.producer.DeleteIssueProducer;
import org.apache.camel.component.jira.producer.FetchCommentsProducer;
import org.apache.camel.component.jira.producer.FetchIssueProducer;
import org.apache.camel.component.jira.producer.TransitionIssueProducer;
import org.apache.camel.component.jira.producer.UpdateIssueProducer;
import org.apache.camel.component.jira.producer.WatcherProducer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jira.JiraConstants.JIRA_REST_CLIENT_FACTORY;

/**
 * The jira component interacts with the JIRA issue tracker.
 * <p>
 * The endpoint encapsulates portions of the JIRA API, relying on the jira-rest-java-client SDK. Available endpoint URIs include:
 * <p>
 * CONSUMERS jira://newIssues (retrieve only new issues after the route is started) jira://newComments (retrieve only new comments after the route is started)
 * <p>
 * PRODUCERS jira://addIssue (add an issue) jira://addComment (add a comment on a given issue) jira://attach (add an attachment on a given issue) jira://deleteIssue (delete a given issue)
 * jira://updateIssue (update fields of a given issue) jira://transitionIssue (transition a status of a given issue) jira://watchers (add/remove watchers of a given issue)
 * <p>
 * The endpoints will respond with jira-rest-java-client POJOs (Issue, Comment, etc.)
 * <p>
 * Note: Rather than webhooks, this endpoint relies on simple polling.  Reasons include: - concerned about reliability/stability if this somehow relied on an exposed, embedded server (Jetty?) - the
 * types of payloads we're polling aren't typically large (plus, paging is available in the API) - need to support apps running somewhere not publicly accessible where a webhook would fail
 */
@UriEndpoint(firstVersion = "3.0", scheme = "jira", title = "Jira", syntax = "jira:type", label = "api,reporting")
public class JiraEndpoint extends DefaultEndpoint {

    private static final transient Logger LOG = LoggerFactory.getLogger(JiraEndpoint.class);

    @UriPath
    @Metadata(required = true)
    private JiraType type;
    @UriParam(label = "consumer")
    private String jql;
    @UriParam(label = "consumer", defaultValue = "50")
    private Integer maxResults = 50;
    @UriParam
    private JiraConfiguration configuration;

    private transient JiraRestClient client;

    public JiraEndpoint(String uri, JiraComponent component, JiraConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public JiraConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        Registry registry = getCamelContext().getRegistry();
        JiraRestClientFactory factory = registry.lookupByNameAndType(JIRA_REST_CLIENT_FACTORY, JiraRestClientFactory.class);
        if (factory == null) {
            factory = new OAuthAsynchronousJiraRestClientFactory();
        }
        final URI jiraServerUri = URI.create(configuration.getJiraUrl());
        if (configuration.getUsername() != null) {
            LOG.info("Jira Basic authentication with username/password.");
            client = factory.createWithBasicHttpAuthentication(jiraServerUri, configuration.getUsername(), configuration.getPassword());
        } else {
            LOG.info("Jira OAuth authentication.");
            JiraOAuthAuthenticationHandler oAuthHandler = new JiraOAuthAuthenticationHandler(configuration.getConsumerKey(),
                    configuration.getVerificationCode(), configuration.getPrivateKey(), configuration.getAccessToken(),
                    configuration.getJiraUrl());
            client = factory.create(jiraServerUri, oAuthHandler);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (client != null) {
            client.close();
        }
    }

    @Override
    public Producer createProducer() {
        switch (type) {
            case ADDISSUE:
                return new AddIssueProducer(this);
            case ATTACH:
                return new AttachFileProducer(this);
            case ADDCOMMENT:
                return new AddCommentProducer(this);
            case WATCHERS:
                return new WatcherProducer(this);
            case DELETEISSUE:
                return new DeleteIssueProducer(this);
            case UPDATEISSUE:
                return new UpdateIssueProducer(this);
            case TRANSITIONISSUE:
                return new TransitionIssueProducer(this);
            case ADDISSUELINK:
                return new AddIssueLinkProducer(this);
            case ADDWORKLOG:
                return new AddWorkLogProducer(this);
            case FETCHISSUE:
                return new FetchIssueProducer(this);
            case FETCHCOMMENTS:
                return new FetchCommentsProducer(this);
            default:
                throw new IllegalArgumentException("Producer does not support type: " + type);
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer;
        if (type == JiraType.NEWCOMMENTS) {
            consumer = new NewCommentsConsumer(this, processor);
        } else if (type == JiraType.NEWISSUES) {
            consumer = new NewIssuesConsumer(this, processor);
        } else {
            throw new IllegalArgumentException("Consumer does not support type: " + type);
        }
        configureConsumer(consumer);
        return consumer;
    }

    public JiraType getType() {
        return type;
    }

    /**
     * Operation to perform. Consumers: NewIssues, NewComments. Producers: AddIssue, AttachFile, DeleteIssue, TransitionIssue, UpdateIssue, Watchers. See this class javadoc description for more
     * information.
     */
    public void setType(JiraType type) {
        this.type = type;
    }

    /**
     * JQL is the query language from JIRA which allows you to retrieve the data you want. For example <tt>jql=project=MyProject</tt> Where MyProject is the product key in Jira. It is important to use
     * the RAW() and set the JQL inside it to prevent camel parsing it, example: RAW(project in (MYP, COM) AND resolution = Unresolved)
     */
    public String getJql() {
        return jql;
    }

    public void setJql(String jql) {
        this.jql = jql;
    }

    public int getDelay() {
        return configuration.getDelay();
    }

    public JiraRestClient getClient() {
        return client;
    }

    public void setClient(JiraRestClient client) {
        this.client = client;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    /**
     * Max number of issues to search for
     */
    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }
}
