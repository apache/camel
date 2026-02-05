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
package org.apache.camel.component.github2;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.github2.consumer.CommitConsumer;
import org.apache.camel.component.github2.consumer.EventsConsumer;
import org.apache.camel.component.github2.consumer.PullRequestCommentConsumer;
import org.apache.camel.component.github2.consumer.PullRequestConsumer;
import org.apache.camel.component.github2.consumer.TagConsumer;
import org.apache.camel.component.github2.event.GitHub2EventFetchStrategy;
import org.apache.camel.component.github2.producer.ClosePullRequestProducer;
import org.apache.camel.component.github2.producer.CreateIssueProducer;
import org.apache.camel.component.github2.producer.GetCommitFileProducer;
import org.apache.camel.component.github2.producer.PullRequestCommentProducer;
import org.apache.camel.component.github2.producer.PullRequestFilesProducer;
import org.apache.camel.component.github2.producer.PullRequestStateProducer;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * Interact with the GitHub API.
 *
 * This component uses the kohsuke github-api library for GitHub API interactions. Available endpoint URIs include:
 *
 * CONSUMERS github2://pullRequest (new pull requests) github2://pullRequestComment (new pull request comments)
 * github2://commit/[branch] (new commits) github2://tag (new tags) github2://event (GitHub events)
 *
 * PRODUCERS github2://pullRequestComment (create a new pull request comment) github2://closePullRequest (close a pull
 * request) github2://pullRequestState (set commit status) github2://pullRequestFiles (get PR files)
 * github2://getCommitFile (get commit file content) github2://createIssue (create an issue)
 *
 * The endpoints respond with org.kohsuke.github POJOs (GHPullRequest, GHCommit, GHIssue, etc.)
 *
 */
@UriEndpoint(firstVersion = "4.18.0", scheme = "github2", title = "GitHub2", syntax = "github2:type/branchName",
             category = { Category.FILE, Category.CLOUD, Category.API }, headersClass = GitHub2Constants.class)
public class GitHub2Endpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    @UriPath
    @Metadata(required = true)
    private GitHub2Type type;
    @UriPath(label = "consumer")
    private String branchName;
    @UriParam(label = "consumer", defaultValue = "last")
    private String startingSha = "last";
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean commitMessageAsBody = true;
    @UriParam(label = "security", secret = true)
    private String oauthToken;
    @UriParam
    @Metadata(required = true)
    private String repoOwner;
    @UriParam
    @Metadata(required = true)
    private String repoName;
    @UriParam(label = "producer", enums = "error,failure,pending,success")
    private String state;
    @UriParam(label = "producer")
    private String targetUrl;
    @UriParam(label = "producer")
    private String encoding;
    @UriParam(label = "consumer,advanced")
    private GitHub2EventFetchStrategy eventFetchStrategy;
    @UriParam(label = "advanced")
    private String apiUrl;

    public GitHub2Endpoint(String uri, GitHub2Component component) {
        super(uri, component);
    }

    @Override
    public String getServiceUrl() {
        return apiUrl != null ? apiUrl : "https://api.github.com";
    }

    @Override
    public String getServiceProtocol() {
        return "rest";
    }

    @Override
    public Producer createProducer() throws Exception {
        if (type == GitHub2Type.CLOSEPULLREQUEST) {
            return new ClosePullRequestProducer(this);
        } else if (type == GitHub2Type.PULLREQUESTCOMMENT) {
            return new PullRequestCommentProducer(this);
        } else if (type == GitHub2Type.PULLREQUESTSTATE) {
            return new PullRequestStateProducer(this);
        } else if (type == GitHub2Type.PULLREQUESTFILES) {
            return new PullRequestFilesProducer(this);
        } else if (type == GitHub2Type.GETCOMMITFILE) {
            return new GetCommitFileProducer(this);
        } else if (type == GitHub2Type.CREATEISSUE) {
            return new CreateIssueProducer(this);
        }
        throw new IllegalArgumentException("Cannot create producer with type " + type);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = null;
        if (type == GitHub2Type.COMMIT) {
            StringHelper.notEmpty(branchName, "branchName", this);
            consumer = new CommitConsumer(this, processor, branchName, startingSha);
        } else if (type == GitHub2Type.PULLREQUEST) {
            consumer = new PullRequestConsumer(this, processor);
        } else if (type == GitHub2Type.PULLREQUESTCOMMENT) {
            consumer = new PullRequestCommentConsumer(this, processor);
        } else if (type == GitHub2Type.TAG) {
            consumer = new TagConsumer(this, processor);
        } else if (type == GitHub2Type.EVENT) {
            consumer = new EventsConsumer(this, processor);
        }

        if (consumer == null) {
            throw new IllegalArgumentException("Cannot create consumer with type " + type);
        }

        configureConsumer(consumer);
        return consumer;
    }

    public GitHub2Type getType() {
        return type;
    }

    /**
     * What git operation to execute
     */
    public void setType(GitHub2Type type) {
        this.type = type;
    }

    public String getBranchName() {
        return branchName;
    }

    /**
     * Name of branch
     */
    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public boolean isCommitMessageAsBody() {
        return commitMessageAsBody;
    }

    /**
     * Whether the commit consumer should store the commit message or the raw org.kohsuke.github.GHCommit object as the
     * message body.
     */
    public void setCommitMessageAsBody(boolean commitMessageAsBody) {
        this.commitMessageAsBody = commitMessageAsBody;
    }

    public String getStartingSha() {
        return startingSha;
    }

    /**
     * The starting sha to use for polling commits with the commit consumer.
     *
     * The value can either be a sha for the sha to start from, or use <tt>beginning</tt> to start from the beginning,
     * or <tt>last</tt> to start from the last commit.
     */
    public void setStartingSha(String startingSha) {
        this.startingSha = startingSha;
    }

    public String getOauthToken() {
        return oauthToken;
    }

    /**
     * GitHub OAuth token. Must be configured on either component or endpoint.
     */
    public void setOauthToken(String oauthToken) {
        this.oauthToken = oauthToken;
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    /**
     * GitHub repository owner (organization)
     */
    public void setRepoOwner(String repoOwner) {
        this.repoOwner = repoOwner;
    }

    public String getRepoName() {
        return repoName;
    }

    /**
     * GitHub repository name
     */
    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getState() {
        return state;
    }

    /**
     * To set git commit status state
     */
    public void setState(String state) {
        this.state = state;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    /**
     * To set git commit status target url
     */
    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * To use the given encoding when getting a git commit file
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public GitHub2EventFetchStrategy getEventFetchStrategy() {
        return eventFetchStrategy;
    }

    /**
     * To specify a custom strategy that configures how the EventsConsumer fetches events.
     */
    public void setEventFetchStrategy(GitHub2EventFetchStrategy eventFetchStrategy) {
        this.eventFetchStrategy = eventFetchStrategy;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    /**
     * GitHub API URL for GitHub Enterprise. Leave empty for github.com.
     */
    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        ObjectHelper.notNull(oauthToken, "oauthToken");
    }
}
