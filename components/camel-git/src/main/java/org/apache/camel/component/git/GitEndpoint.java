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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.git.consumer.GitBranchConsumer;
import org.apache.camel.component.git.consumer.GitCommitConsumer;
import org.apache.camel.component.git.consumer.GitTagConsumer;
import org.apache.camel.component.git.consumer.GitType;
import org.apache.camel.component.git.producer.GitProducer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * Perform operations on git repositories.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "git", title = "Git", syntax = "git:localPath", category = { Category.FILE },
             headersClass = GitConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String",
                                   description = "The full message of the commit with type=commit, the name of the ref,"
                                                 + " such as refs/heads/main or refs/tags/v1, with type=branch and tag. The"
                                                 + " commit id, author, committer and time, or the object id of the ref, are"
                                                 + " in headers"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: the operations take their input from the options and headers,"
                                                + " such as the file in CamelGitFilename and the message in"
                                                + " CamelGitCommitMessage"),
           producerOut = @BodyType(value = { Carrier.UNCHANGED, Carrier.POJO, Carrier.LIST, Carrier.ITERATOR, Carrier.MAP },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "status", carriers = Carrier.POJO,
                                                          javaType = "org.eclipse.jgit.api.Status"),
                                           @BodyType.When(option = "operation", value = "log", carriers = Carrier.ITERATOR,
                                                          javaType = "org.eclipse.jgit.revwalk.RevWalk"),
                                           @BodyType.When(option = "operation", value = "push", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "pushTag", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "pull", carriers = Carrier.POJO,
                                                          javaType = "org.eclipse.jgit.api.PullResult"),
                                           @BodyType.When(option = "operation", value = "merge", carriers = Carrier.POJO,
                                                          javaType = "org.eclipse.jgit.api.MergeResult"),
                                           @BodyType.When(option = "operation", value = "cherryPick", carriers = Carrier.POJO,
                                                          javaType = "org.eclipse.jgit.api.CherryPickResult"),
                                           @BodyType.When(option = "operation", value = "showBranches",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "showTags", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "clean", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "gc", carriers = Carrier.MAP,
                                                          javaType = "java.util.Properties"),
                                           @BodyType.When(option = "operation", value = "remoteAdd", carriers = Carrier.POJO,
                                                          javaType = "org.eclipse.jgit.transport.RemoteConfig"),
                                           @BodyType.When(option = "operation", value = "remoteList", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "add", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "checkout",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "clone", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "commit", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "commitAll",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "createBranch",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "createTag",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "deleteBranch",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "deleteTag",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "init", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "remove",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "The result of JGit for the operation: the Status of status, the commits of"
                                                 + " log as a RevWalk read once, a List of Refs for showBranches and"
                                                 + " showTags, the PushResults of push, the Properties of the statistics of"
                                                 + " gc. The operations that change the repository, such as add and commit,"
                                                 + " keep the body. Without the operation option the operation is taken"
                                                 + " from the CamelGitOperation header"))
public class GitEndpoint extends ScheduledPollEndpoint {

    @UriPath
    @Metadata(required = true)
    private String localPath;

    @UriParam
    private String branchName;

    @UriParam(label = "producer",
              description = "Name of target branch in merge operation. If not supplied will try to use init.defaultBranch git configs. If not configured will use default value",
              defaultValue = "master")
    private String targetBranchName;

    @UriParam(label = "producer")
    private String tagName;

    @UriParam(enums = "commit,tag,branch", label = "consumer")
    private GitType type;

    @UriParam(label = "producer,security", security = "secret")
    private String username;

    @UriParam(label = "producer,security", security = "secret")
    private String password;

    @UriParam(label = "producer")
    private String remotePath;

    @UriParam(label = "producer")
    private String remoteName;

    // Set to true for backward compatibility , better to set to false (native git behavior)
    @UriParam(defaultValue = "true")
    @Metadata(label = "producer")
    private boolean allowEmpty = true;

    @UriParam(label = "producer",
              description = "Clone depth for shallow clones. Must be a positive integer. "
                            + "A value of 1 fetches only the latest commit. "
                            + "When set to 0 or not specified, a full clone is performed.")
    private int depth;

    @UriParam(label = "producer",
              enums = "add,checkout,cherryPick,clean,clone,commit,commitAll,createBranch,createTag,deleteBranch,deleteTag,gc,init,log,merge,pull,push,pushTag,remoteAdd,remoteList,remove,showBranches,showTags,status")
    private String operation;

    @UriParam(description = "A String with path to a .gitconfig file", label = "advanced")
    private String gitConfigFile;

    public GitEndpoint(String uri, GitComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GitProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (type == null) {
            throw new IllegalArgumentException("type must be specified for git consumer");
        }

        return switch (type) {
            case COMMIT -> new GitCommitConsumer(this, processor);
            case TAG -> new GitTagConsumer(this, processor);
            case BRANCH -> new GitBranchConsumer(this, processor);
        };
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

    /**
     * Clone depth for shallow clones
     */
    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * The branch name to merge
     */
    public String getTargetBranchName() {
        return this.targetBranchName;
    }

    public void setTargetBranchName(String targetBranchName) {
        this.targetBranchName = targetBranchName;
    }

    /**
     * A String with path to a .gitconfig file
     */
    public String getGitConfigFile() {
        return this.gitConfigFile;
    }

    public void setGitConfigFile(String gitConfigFile) {
        this.gitConfigFile = gitConfigFile;
    }
}
