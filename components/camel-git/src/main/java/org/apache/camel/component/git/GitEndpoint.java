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
package org.apache.camel.component.git;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.git.consumer.GitBranchConsumer;
import org.apache.camel.component.git.consumer.GitCommitConsumer;
import org.apache.camel.component.git.consumer.GitTagConsumer;
import org.apache.camel.component.git.consumer.GitType;
import org.apache.camel.component.git.producer.GitProducer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * The git component is used for working with git repositories.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "git", title = "Git", syntax = "git:localPath", label = "file")
public class GitEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true)
    private String localPath;

    @UriParam
    private String branchName;

    @UriParam
    private String tagName;

    @UriParam(enums = "commit,tag,branch", label = "consumer")
    private GitType type;

    @UriParam
    private String username;

    @UriParam
    private String password;

    @UriParam
    private String remotePath;

    @UriParam
    private String remoteName;

    // Set to true for backward compatibility , better to set to false (native git behavior)
    @UriParam(defaultValue = "true")
    @Metadata(label = "producer")
    private boolean allowEmpty = true;

    @UriParam(enums = "clone,init,add,remove,commit,commitAll,createBranch,deleteBranch,createTag,deleteTag,status,log,push,pull,showBranches,cherryPick,remoteAdd,remoteList", label = "producer")
    private String operation;

    public GitEndpoint(String uri, GitComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GitProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (type == GitType.COMMIT) {
            return new GitCommitConsumer(this, processor);
        } else if (type == GitType.TAG) {
            return new GitTagConsumer(this, processor);
        } else if (type == GitType.BRANCH) {
            return new GitBranchConsumer(this, processor);
        } else {
            throw new IllegalArgumentException("Cannot create consumer with type " + type);
        }
    }

    /**
     * The remote repository path
     */
    public String getRemotePath() {
        return remotePath;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    /**
     * The branch name to work on
     */
    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    /**
     * Remote repository username
     */
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Remote repository password
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Local repository path
     */
    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    /**
     * The operation to do on the repository
     */
    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    /**
     * The consumer type
     */
    public GitType getType() {
        return type;
    }

    public void setType(GitType type) {
        this.type = type;
    }

    /**
     * The tag name to work on
     */
    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    /**
     * The remote repository name to use in particular operation like pull
     */
    public String getRemoteName() {
        return remoteName;
    }

    public void setRemoteName(String remoteName) {
        this.remoteName = remoteName;
    }

    /**
     * The flag to manage empty git commits
     */
    public boolean isAllowEmpty() {
        return allowEmpty;
    }

    public void setAllowEmpty(boolean allowEmpty) {
        this.allowEmpty = allowEmpty;
    }
}
