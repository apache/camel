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
package org.apache.camel.component.github;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.github.consumer.CommitConsumer;
import org.apache.camel.component.github.consumer.PullRequestCommentConsumer;
import org.apache.camel.component.github.consumer.PullRequestConsumer;
import org.apache.camel.component.github.consumer.TagConsumer;
import org.apache.camel.component.github.producer.ClosePullRequestProducer;
import org.apache.camel.component.github.producer.CreateIssueProducer;
import org.apache.camel.component.github.producer.GetCommitFileProducer;
import org.apache.camel.component.github.producer.PullRequestCommentProducer;
import org.apache.camel.component.github.producer.PullRequestFilesProducer;
import org.apache.camel.component.github.producer.PullRequestStateProducer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.StringHelper;

/**
 * The github component is used for integrating Camel with github.
 *
 * The endpoint encapsulates portions of the GitHub API, relying on the org.eclipse.egit.github.core Java SDK.
 * Available endpoint URIs include:
 *
 * CONSUMERS
 * github://pullRequest (new pull requests)
 * github://pullRequestComment (new pull request comments)
 * github://commit/[branch] (new commits)
 * github://tag (new tags)
 *
 * PRODUCERS
 * github://pullRequestComment (create a new pull request comment; see PullRequestCommentProducer for header requirements)
 *
 * The endpoints will respond with org.eclipse.egit.github.core-provided POJOs (PullRequest, CommitComment,
 * RepositoryTag, RepositoryCommit, etc.)
 *
 * Note: Rather than webhooks, this endpoint relies on simple polling.  Reasons include:
 * - concerned about reliability/stability if this somehow relied on an exposed, embedded server (Jetty?)
 * - the types of payloads we're polling aren't typically large (plus, paging is available in the API)
 * - need to support apps running somewhere not publicly accessible where a webhook would fail
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "github", title = "GitHub", syntax = "github:type/branchName", label = "api,file")
public class GitHubEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = true)
    private GitHubType type;
    @UriPath(label = "consumer")
    private String branchName;
    @UriParam
    private String username;
    @UriParam
    private String password;
    @UriParam
    private String oauthToken;
    @UriParam @Metadata(required = true)
    private String repoOwner;
    @UriParam @Metadata(required = true)
    private String repoName;
    @UriParam(label = "producer", enums = "error,failure,pending,success")
    private String state;
    @UriParam(label = "producer")
    private String targetUrl;
    @UriParam(label = "producer")
    private String encoding;

    public GitHubEndpoint(String uri, GitHubComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        if (type == GitHubType.CLOSEPULLREQUEST) {
            return new ClosePullRequestProducer(this);
        } else if (type == GitHubType.PULLREQUESTCOMMENT) {
            return new PullRequestCommentProducer(this);
        } else if (type == GitHubType.PULLREQUESTSTATE) {
            return new PullRequestStateProducer(this);
        } else if (type == GitHubType.PULLREQUESTFILES) {
            return new PullRequestFilesProducer(this);
        } else if (type == GitHubType.GETCOMMITFILE) {
            return new GetCommitFileProducer(this);
        } else if (type == GitHubType.CREATEISSUE) {
            return new CreateIssueProducer(this);
        }
        throw new IllegalArgumentException("Cannot create producer with type " + type);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (type == GitHubType.COMMIT) {
            StringHelper.notEmpty(branchName, "branchName", this);
            return new CommitConsumer(this, processor, branchName);
        } else if (type == GitHubType.PULLREQUEST) {
            return new PullRequestConsumer(this, processor);
        } else if (type == GitHubType.PULLREQUESTCOMMENT) {
            return new PullRequestCommentConsumer(this, processor);
        } else if (type == GitHubType.TAG) {
            return new TagConsumer(this, processor);
        }
        throw new IllegalArgumentException("Cannot create consumer with type " + type);
    }

    public GitHubType getType() {
        return type;
    }

    /**
     * What git operation to execute
     */
    public void setType(GitHubType type) {
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

    public String getUsername() {
        return username;
    }

    /**
     * GitHub username, required unless oauthToken is provided
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * GitHub password, required unless oauthToken is provided
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getOauthToken() {
        return oauthToken;
    }

    /**
     * GitHub OAuth token, required unless username & password are provided
     */
    public void setOauthToken(String oauthToken) {
        this.oauthToken = oauthToken;
    }

    public boolean hasOauth() {
        return oauthToken != null && oauthToken.length() > 0;
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
}
