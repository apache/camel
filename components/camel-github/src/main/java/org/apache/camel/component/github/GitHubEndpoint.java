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
package org.apache.camel.component.github;

import java.util.regex.Pattern;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.github.consumer.CommitConsumer;
import org.apache.camel.component.github.consumer.ConsumerType;
import org.apache.camel.component.github.consumer.PullRequestCommentConsumer;
import org.apache.camel.component.github.consumer.PullRequestConsumer;
import org.apache.camel.component.github.consumer.TagConsumer;
import org.apache.camel.component.github.producer.ClosePullRequestProducer;
import org.apache.camel.component.github.producer.ProducerType;
import org.apache.camel.component.github.producer.PullRequestCommentProducer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
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
@UriEndpoint(scheme = "github")
@UriParams
public class GitHubEndpoint extends DefaultEndpoint {
    @UriParam
    private String username;
    @UriParam
    private String password;
    @UriParam
    private String oauthToken;
    @UriParam
    private String repoOwner;
    @UriParam
    private String repoName;
    
    public GitHubEndpoint(String uri, GitHubComponent component) {
        super(uri, component);
    }

    public Producer createProducer() throws Exception {
        String uri = getEndpointUri();
        String[] uriSplit = splitUri(getEndpointUri());
        
        if (uriSplit.length > 0) {
            switch (ProducerType.fromUri(uriSplit[0])) {
            case CLOSEPULLREQUEST:
                return new ClosePullRequestProducer(this);
            case PULLREQUESTCOMMENT:
                return new PullRequestCommentProducer(this);
            default:
                break;
            }
        }

        throw new IllegalArgumentException("Cannot create any producer with uri " + uri
                + ". A producer type was not provided (or an incorrect pairing was used).");
    }
    
    public Consumer createConsumer(Processor processor) throws Exception {
        String uri = getEndpointUri();
        String[] uriSplit = splitUri(getEndpointUri());
        
        if (uriSplit.length > 0) {
            switch (ConsumerType.fromUri(uriSplit[0])) {
            case COMMIT:
                if (uriSplit.length >= 2 && uriSplit[1].length() > 1) {
                    return new CommitConsumer(uriSplit[1], this, processor);
                } else {
                    throw new IllegalArgumentException("Must provide a branch name when using the COMMIT consumer.  github://commit/[branch name]?[options]");
                }
            case PULLREQUEST:
                return new PullRequestConsumer(this, processor);
            case PULLREQUESTCOMMENT:
                return new PullRequestCommentConsumer(this, processor);
            case TAG:
                return new TagConsumer(this, processor);
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
        Pattern p1 = Pattern.compile("github:(//)*");
        Pattern p2 = Pattern.compile("\\?.*");

        uri = p1.matcher(uri).replaceAll("");
        uri = p2.matcher(uri).replaceAll("");

        return uri.split("/");
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

    public String getOauthToken() {
        return oauthToken;
    }

    public void setOauthToken(String oauthToken) {
        this.oauthToken = oauthToken;
    }
    
    public boolean hasOauth() {
        return oauthToken != null && oauthToken.length() > 0;
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    public void setRepoOwner(String repoOwner) {
        this.repoOwner = repoOwner;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }
}
