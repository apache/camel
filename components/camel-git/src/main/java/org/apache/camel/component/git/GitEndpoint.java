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
 * Run git operations, such as clone, commit, push and pull, on a local repository with JGit, and receive its commits,
 * branches and tags.
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

    @UriPath(description = "The path of the working tree of the local repository, the directory that holds the .git"
                           + " directory. The clone operation creates the repository there and fails if the directory exists,"
                           + " and init creates it there.")
    @Metadata(required = true)
    private String localPath;

    @UriParam(description = "The name of the branch. The commit consumer receives the commits reachable from it, or the"
                            + " commits of all refs when it is not set or not found. For the producer it is the branch to"
                            + " create or delete, to check out (clone and checkout, which creates it when it does not exist) or"
                            + " to merge into targetBranchName (merge), and add, remove, commit, commitAll, status, log, push,"
                            + " pull, cherryPick and clean check out this existing branch first.")
    private String branchName;

    @UriParam(label = "producer",
              description = "The branch that the merge operation checks out and merges branchName into. If not set, merge"
                            + " uses init.defaultBranch of the user git config, or master when that is not set either.",
              defaultValue = "master")
    private String targetBranchName;

    @UriParam(label = "producer",
              description = "The name of the tag for createTag, which creates an annotated tag of HEAD, and for deleteTag and"
                            + " pushTag. For checkout, the tag that the new branchName branch starts from.")
    private String tagName;

    @UriParam(enums = "commit,tag,branch", label = "consumer",
              description = "What the consumer receives: commit for each commit, tag for each tag, or branch for each local"
                            + " and remote-tracking branch. Required for the consumer. It receives all the existing items on"
                            + " its first poll and keeps the consumed ones in memory only, so after a restart it receives all"
                            + " of them again.")
    private GitType type;

    @UriParam(label = "producer,security", security = "secret",
              description = "The user name to authenticate with the remote repository in clone, pull, push and pushTag. Used"
                            + " only when password is set too.")
    private String username;

    @UriParam(label = "producer,security", security = "secret",
              description = "The password, or an access token, to authenticate with the remote repository in clone, pull, push"
                            + " and pushTag. Used only when username is set too.")
    private String password;

    @UriParam(label = "producer",
              description = "The URL of the remote repository, such as https://github.com/apache/camel.git. The clone"
                            + " operation clones it, and remoteAdd adds it as the remote named remoteName.")
    private String remotePath;

    @UriParam(label = "producer",
              description = "The name of the remote, such as origin, that pull, push and pushTag use and that remoteAdd adds."
                            + " Required by these operations.")
    private String remoteName;

    // Set to true for backward compatibility , better to set to false (native git behavior)
    @UriParam(defaultValue = "true",
              description = "Whether commit and commitAll create a commit when there is nothing to commit. If false, they fail"
                            + " with the EmptyCommitException of JGit instead, as git commit does without --allow-empty. The"
                            + " CamelGitAllowEmpty header replaces this option.")
    @Metadata(label = "producer")
    private boolean allowEmpty = true;

    @UriParam(label = "producer",
              description = "The depth of a shallow clone in the clone operation, as the number of commits of history to"
                            + " fetch. For example, 1 fetches only the latest commit. The default 0 makes a full clone, and a"
                            + " negative value fails the clone.")
    private int depth;

    @UriParam(label = "producer",
              enums = "add,checkout,cherryPick,clean,clone,commit,commitAll,createBranch,createTag,deleteBranch,deleteTag,gc,init,log,merge,pull,push,pushTag,remoteAdd,remoteList,remove,showBranches,showTags,status",
              description = "The operation that the producer performs. If not set, the producer takes it from the"
                            + " CamelGitOperation header.")
    private String operation;

    @UriParam(description = "The git config file to read in place of the user git config (~/.gitconfig), as a URI with the"
                            + " classpath:, file:, http: or https: scheme. When it is set, the system git config is not read.",
              label = "advanced")
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

        Consumer consumer = switch (type) {
            case COMMIT -> new GitCommitConsumer(this, processor);
            case TAG -> new GitTagConsumer(this, processor);
            case BRANCH -> new GitBranchConsumer(this, processor);
        };
        configureConsumer(consumer);
        return consumer;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
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

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public GitType getType() {
        return type;
    }

    public void setType(GitType type) {
        this.type = type;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getRemoteName() {
        return remoteName;
    }

    public void setRemoteName(String remoteName) {
        this.remoteName = remoteName;
    }

    public boolean isAllowEmpty() {
        return allowEmpty;
    }

    public void setAllowEmpty(boolean allowEmpty) {
        this.allowEmpty = allowEmpty;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public String getTargetBranchName() {
        return this.targetBranchName;
    }

    public void setTargetBranchName(String targetBranchName) {
        this.targetBranchName = targetBranchName;
    }

    public String getGitConfigFile() {
        return this.gitConfigFile;
    }

    public void setGitConfigFile(String gitConfigFile) {
        this.gitConfigFile = gitConfigFile;
    }
}
