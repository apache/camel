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
package org.apache.camel.component.git.producer;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.component.RepositoryFactory;
import org.apache.camel.component.git.GitConstants;
import org.apache.camel.component.git.GitEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jgit.api.CherryPickResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GitProducer.class);

    private final GitEndpoint endpoint;

    private Repository repo;

    private Git git;

    public GitProducer(GitEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.repo = getLocalRepository();
        this.git = new Git(repo);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        repo.close();
        git.close();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation;
        if (ObjectHelper.isEmpty(endpoint.getOperation())) {
            operation = exchange.getIn().getHeader(GitConstants.GIT_OPERATION, String.class);
        } else {
            operation = endpoint.getOperation();
        }
        if (ObjectHelper.isEmpty(endpoint.getLocalPath())) {
            throw new IllegalArgumentException("Local path must specified to execute " + operation);
        }

        switch (operation) {

            case GitOperation.CLONE_OPERATION:
                doClone(operation);
                break;

            case GitOperation.CHECKOUT_OPERATION:
                doCheckout(operation);
                break;

            case GitOperation.INIT_OPERATION:
                doInit(operation);
                break;

            case GitOperation.ADD_OPERATION:
                doAdd(exchange, operation);
                break;

            case GitOperation.CHERRYPICK_OPERATION:
                doCherryPick(exchange, operation);
                break;

            case GitOperation.REMOVE_OPERATION:
                doRemove(exchange, operation);
                break;

            case GitOperation.COMMIT_OPERATION:
                doCommit(exchange, operation);
                break;

            case GitOperation.COMMIT_ALL_OPERATION:
                doCommitAll(exchange, operation);
                break;

            case GitOperation.CREATE_BRANCH_OPERATION:
                doCreateBranch(operation);
                break;

            case GitOperation.DELETE_BRANCH_OPERATION:
                doDeleteBranch(operation);
                break;

            case GitOperation.STATUS_OPERATION:
                doStatus(exchange, operation);
                break;

            case GitOperation.LOG_OPERATION:
                doLog(exchange, operation);
                break;

            case GitOperation.PUSH_OPERATION:
                doPush(exchange, operation);
                break;

            case GitOperation.PUSH_TAG_OPERATION:
                doPushTag(exchange, operation);
                break;

            case GitOperation.PULL_OPERATION:
                doPull(exchange, operation);
                break;

            case GitOperation.MERGE_OPERATION:
                doMerge(exchange, operation);
                break;

            case GitOperation.CREATE_TAG_OPERATION:
                doCreateTag(operation);
                break;

            case GitOperation.DELETE_TAG_OPERATION:
                doDeleteTag(operation);
                break;

            case GitOperation.SHOW_BRANCHES_OPERATION:
                doShowBranches(exchange, operation);
                break;

            case GitOperation.SHOW_TAGS_OPERATION:
                doShowTags(exchange, operation);
                break;

            case GitOperation.CLEAN_OPERATION:
                doClean(exchange, operation);
                break;

            case GitOperation.GC_OPERATION:
                doGc(exchange, operation);
                break;

            case GitOperation.REMOTE_ADD_OPERATION:
                doRemoteAdd(exchange, operation);
                break;

            case GitOperation.REMOTE_LIST_OPERATION:
                doRemoteList(exchange, operation);
                break;

            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    protected void doClone(String operation) throws GitAPIException {
        Git result = null;
        if (ObjectHelper.isEmpty(endpoint.getLocalPath())) {
            throw new IllegalArgumentException("Local path must specified to execute " + operation);
        }
        try {
            File localRepo = new File(endpoint.getLocalPath(), "");
            if (!localRepo.exists()) {
                if (ObjectHelper.isNotEmpty(endpoint.getUsername()) && ObjectHelper.isNotEmpty(endpoint.getPassword())) {
                    UsernamePasswordCredentialsProvider credentials
                            = new UsernamePasswordCredentialsProvider(endpoint.getUsername(), endpoint.getPassword());
                    if (ObjectHelper.isEmpty(endpoint.getBranchName())) {
                        result = Git.cloneRepository().setCredentialsProvider(credentials).setURI(endpoint.getRemotePath())
                                .setDirectory(new File(endpoint.getLocalPath(), ""))
                                .call();
                    } else {
                        result = Git.cloneRepository().setCredentialsProvider(credentials).setURI(endpoint.getRemotePath())
                                .setDirectory(new File(endpoint.getLocalPath(), ""))
                                .setBranch(endpoint.getBranchName()).call();
                    }
                } else {
                    if (ObjectHelper.isEmpty(endpoint.getBranchName())) {
                        result = Git.cloneRepository().setURI(endpoint.getRemotePath())
                                .setDirectory(new File(endpoint.getLocalPath(), "")).call();
                    } else {
                        result = Git.cloneRepository().setURI(endpoint.getRemotePath())
                                .setDirectory(new File(endpoint.getLocalPath(), "")).setBranch(endpoint.getBranchName())
                                .call();
                    }
                }
            } else {
                throw new IllegalArgumentException("The local repository directory already exists");
            }
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        } finally {
            if (ObjectHelper.isNotEmpty(result)) {
                result.close();
            }
        }
    }

    protected void doCheckout(String operation) throws GitAPIException {
        if (ObjectHelper.isEmpty(endpoint.getBranchName())) {
            throw new IllegalArgumentException("Branch Name must be specified to execute " + operation);
        }
        try {
            if (ObjectHelper.isEmpty(endpoint.getTagName())) {
                git.checkout().setCreateBranch(true).setName(endpoint.getBranchName()).call();
            } else {
                git.checkout().setCreateBranch(true).setName(endpoint.getBranchName()).setStartPoint(endpoint.getTagName())
                        .call();
            }
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
    }

    protected void doInit(String operation) throws GitAPIException {
        Git result = null;
        if (ObjectHelper.isEmpty(endpoint.getLocalPath())) {
            throw new IllegalArgumentException("Local path must specified to execute " + operation);
        }
        try {
            result = Git.init().setDirectory(new File(endpoint.getLocalPath(), "")).setBare(false).call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        } finally {
            if (ObjectHelper.isNotEmpty(result)) {
                result.close();
            }
        }
    }

    protected void doAdd(Exchange exchange, String operation) throws GitAPIException {
        String fileName = null;
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_FILE_NAME))) {
            fileName = exchange.getIn().getHeader(GitConstants.GIT_FILE_NAME, String.class);
        } else {
            throw new IllegalArgumentException("File name must be specified to execute " + operation);
        }
        try {
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            git.add().addFilepattern(fileName).call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
    }

    protected void doRemove(Exchange exchange, String operation) throws GitAPIException {
        String fileName = null;
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_FILE_NAME))) {
            fileName = exchange.getIn().getHeader(GitConstants.GIT_FILE_NAME, String.class);
        } else {
            throw new IllegalArgumentException("File name must be specified to execute " + operation);
        }
        try {
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            git.rm().addFilepattern(fileName).call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
    }

    protected void doCommit(Exchange exchange, String operation) throws GitAPIException {
        String commitMessage = null;
        String username = null;
        String email = null;
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_COMMIT_MESSAGE))) {
            commitMessage = exchange.getIn().getHeader(GitConstants.GIT_COMMIT_MESSAGE, String.class);
        } else {
            throw new IllegalArgumentException("Commit message must be specified to execute " + operation);
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_COMMIT_USERNAME))
                && ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_COMMIT_EMAIL))) {
            username = exchange.getIn().getHeader(GitConstants.GIT_COMMIT_USERNAME, String.class);
            email = exchange.getIn().getHeader(GitConstants.GIT_COMMIT_EMAIL, String.class);
        }
        boolean allowEmpty = endpoint.isAllowEmpty();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_ALLOW_EMPTY))) {
            allowEmpty = exchange.getIn().getHeader(GitConstants.GIT_ALLOW_EMPTY, Boolean.class);
        }

        try {
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            if (ObjectHelper.isNotEmpty(username) && ObjectHelper.isNotEmpty(email)) {
                git.commit().setAllowEmpty(allowEmpty).setCommitter(username, email).setMessage(commitMessage).call();
            } else {
                git.commit().setAllowEmpty(allowEmpty).setMessage(commitMessage).call();
            }
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
    }

    protected void doCommitAll(Exchange exchange, String operation) throws GitAPIException {
        String commitMessage = null;
        String username = null;
        String email = null;
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_COMMIT_MESSAGE))) {
            commitMessage = exchange.getIn().getHeader(GitConstants.GIT_COMMIT_MESSAGE, String.class);
        } else {
            throw new IllegalArgumentException("Commit message must be specified to execute " + operation);
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_COMMIT_USERNAME))
                && ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_COMMIT_EMAIL))) {
            username = exchange.getIn().getHeader(GitConstants.GIT_COMMIT_USERNAME, String.class);
            email = exchange.getIn().getHeader(GitConstants.GIT_COMMIT_EMAIL, String.class);
        }
        boolean allowEmpty = endpoint.isAllowEmpty();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_ALLOW_EMPTY))) {
            allowEmpty = exchange.getIn().getHeader(GitConstants.GIT_ALLOW_EMPTY, Boolean.class);
        }

        try {
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            if (ObjectHelper.isNotEmpty(username) && ObjectHelper.isNotEmpty(email)) {
                git.commit().setAllowEmpty(allowEmpty).setAll(true).setCommitter(username, email).setMessage(commitMessage)
                        .call();
            } else {
                git.commit().setAllowEmpty(allowEmpty).setAll(true).setMessage(commitMessage).call();
            }
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
    }

    protected void doCreateBranch(String operation) throws GitAPIException {
        if (ObjectHelper.isEmpty(endpoint.getBranchName())) {
            throw new IllegalArgumentException("Branch Name must be specified to execute " + operation);
        }
        try {
            git.branchCreate().setName(endpoint.getBranchName()).call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
    }

    protected void doDeleteBranch(String operation) throws GitAPIException {
        if (ObjectHelper.isEmpty(endpoint.getBranchName())) {
            throw new IllegalArgumentException("Branch Name must be specified to execute " + operation);
        }
        try {
            git.branchDelete().setBranchNames(endpoint.getBranchName()).call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
    }

    protected void doStatus(Exchange exchange, String operation) throws GitAPIException {
        Status status = null;
        try {
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            status = git.status().call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
        updateExchange(exchange, status);
    }

    protected void doLog(Exchange exchange, String operation) throws GitAPIException {
        Iterable<RevCommit> revCommit = null;
        try {
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            revCommit = git.log().call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
        updateExchange(exchange, revCommit);
    }

    protected void doPush(Exchange exchange, String operation) throws GitAPIException {
        Iterable<PushResult> result = null;
        try {
            if (ObjectHelper.isEmpty(endpoint.getRemoteName())) {
                throw new IllegalArgumentException("Remote name must be specified to execute " + operation);
            }
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            if (ObjectHelper.isNotEmpty(endpoint.getUsername()) && ObjectHelper.isNotEmpty(endpoint.getPassword())) {
                UsernamePasswordCredentialsProvider credentials
                        = new UsernamePasswordCredentialsProvider(endpoint.getUsername(), endpoint.getPassword());
                result = git.push().setCredentialsProvider(credentials).setRemote(endpoint.getRemoteName()).call();
            } else {
                result = git.push().setRemote(endpoint.getRemoteName()).call();
            }
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
        updateExchange(exchange, result);
    }

    protected void doPushTag(Exchange exchange, String operation) throws GitAPIException {
        Iterable<PushResult> result = null;
        try {
            if (ObjectHelper.isEmpty(endpoint.getRemoteName())) {
                throw new IllegalArgumentException("Remote name must be specified to execute " + operation);
            }
            if (ObjectHelper.isEmpty(endpoint.getTagName())) {
                throw new IllegalArgumentException("Tag Name must be specified to execute " + operation);
            }
            if (ObjectHelper.isNotEmpty(endpoint.getUsername()) && ObjectHelper.isNotEmpty(endpoint.getPassword())) {
                UsernamePasswordCredentialsProvider credentials
                        = new UsernamePasswordCredentialsProvider(endpoint.getUsername(), endpoint.getPassword());
                result = git.push().setCredentialsProvider(credentials).setRemote(endpoint.getRemoteName())
                        .add(Constants.R_TAGS + endpoint.getTagName()).call();
            } else {
                result = git.push().setRemote(endpoint.getRemoteName()).add(Constants.R_TAGS + endpoint.getTagName()).call();
            }
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
        updateExchange(exchange, result);
    }

    protected void doPull(Exchange exchange, String operation) throws GitAPIException {
        PullResult result = null;
        try {
            if (ObjectHelper.isEmpty(endpoint.getRemoteName())) {
                throw new IllegalArgumentException("Remote name must be specified to execute " + operation);
            }
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            if (ObjectHelper.isNotEmpty(endpoint.getUsername()) && ObjectHelper.isNotEmpty(endpoint.getPassword())) {
                UsernamePasswordCredentialsProvider credentials
                        = new UsernamePasswordCredentialsProvider(endpoint.getUsername(), endpoint.getPassword());
                result = git.pull().setCredentialsProvider(credentials).setRemote(endpoint.getRemoteName()).call();
            } else {
                result = git.pull().setRemote(endpoint.getRemoteName()).call();
            }
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
        updateExchange(exchange, result);
    }

    protected void doMerge(Exchange exchange, String operation) throws ConfigInvalidException, GitAPIException, IOException {
        MergeResult result = null;
        ObjectId mergeBase;
        try {
            if (ObjectHelper.isEmpty(endpoint.getBranchName())) {
                throw new IllegalArgumentException("Branch name must be specified to execute " + operation);
            }
            mergeBase = git.getRepository().resolve(endpoint.getBranchName());
            git.checkout().setName(defineTargetBranchName()).call();
            result = git.merge().include(mergeBase).setFastForward(FastForwardMode.FF).setCommit(true).call();
        } catch (ConfigInvalidException | GitAPIException | IOException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
        updateExchange(exchange, result);
    }

    private String defineTargetBranchName() throws ConfigInvalidException, IOException {
        if (ObjectHelper.isNotEmpty(endpoint.getTargetBranchName())) {
            return endpoint.getTargetBranchName();
        }

        String defaultBranch = SystemReader.getInstance().getUserConfig().getString(ConfigConstants.CONFIG_INIT_SECTION, null,
                ConfigConstants.CONFIG_KEY_DEFAULT_BRANCH);

        return ObjectHelper.isNotEmpty(defaultBranch) ? defaultBranch : "master";
    }

    protected void doCreateTag(String operation) throws GitAPIException {
        if (ObjectHelper.isEmpty(endpoint.getTagName())) {
            throw new IllegalArgumentException("Tag Name must be specified to execute " + operation);
        }
        try {
            git.tag().setName(endpoint.getTagName()).call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
    }

    protected void doDeleteTag(String operation) throws GitAPIException {
        if (ObjectHelper.isEmpty(endpoint.getTagName())) {
            throw new IllegalArgumentException("Tag Name must be specified to execute " + operation);
        }
        try {
            git.tagDelete().setTags(endpoint.getTagName()).call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
    }

    protected void doShowBranches(Exchange exchange, String operation) throws GitAPIException {
        List<Ref> result = null;
        try {
            result = git.branchList().setListMode(ListMode.ALL).call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
        updateExchange(exchange, result);
    }

    protected void doShowTags(Exchange exchange, String operation) throws GitAPIException {
        List<Ref> result = null;
        try {
            result = git.tagList().call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
        updateExchange(exchange, result);
    }

    protected void doCherryPick(Exchange exchange, String operation) throws GitAPIException, IOException {
        CherryPickResult result = null;
        String commitId = null;
        try {
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(GitConstants.GIT_COMMIT_ID))) {
                commitId = exchange.getIn().getHeader(GitConstants.GIT_COMMIT_ID, String.class);
            } else {
                throw new IllegalArgumentException("Commit id must be specified to execute " + operation);
            }
            RevWalk walk = new RevWalk(repo);
            ObjectId id = repo.resolve(commitId);
            RevCommit commit = walk.parseCommit(id);
            walk.dispose();
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            result = git.cherryPick().include(commit).call();
        } catch (GitAPIException | IOException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
        updateExchange(exchange, result);
    }

    protected void doClean(Exchange exchange, String operation) throws GitAPIException {
        Set<String> result = null;
        try {
            if (ObjectHelper.isNotEmpty(endpoint.getBranchName())) {
                git.checkout().setCreateBranch(false).setName(endpoint.getBranchName()).call();
            }
            result = git.clean().setCleanDirectories(true).call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
        updateExchange(exchange, result);
    }

    protected void doGc(Exchange exchange, String operation) throws GitAPIException {
        Properties result = null;
        try {
            result = git.gc().call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
        updateExchange(exchange, result);
    }

    protected void doRemoteAdd(Exchange exchange, String operation) throws GitAPIException, URISyntaxException {
        if (ObjectHelper.isEmpty(endpoint.getRemoteName())) {
            throw new IllegalArgumentException("Remote Name must be specified to execute " + operation);
        }
        if (ObjectHelper.isEmpty(endpoint.getRemotePath())) {
            throw new IllegalArgumentException("Remote Path must be specified to execute " + operation);
        }
        RemoteConfig result = null;
        try {
            RemoteAddCommand remoteAddCommand = git.remoteAdd();
            remoteAddCommand.setUri(new URIish(endpoint.getRemotePath()));
            remoteAddCommand.setName(endpoint.getRemoteName());
            result = remoteAddCommand.call();
        } catch (GitAPIException | URISyntaxException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
        updateExchange(exchange, result);
    }

    protected void doRemoteList(Exchange exchange, String operation) throws GitAPIException {
        List<RemoteConfig> result = null;

        try {
            result = git.remoteList().call();
        } catch (GitAPIException e) {
            LOG.error("There was an error in Git {} operation", operation);
            throw e;
        }
        updateExchange(exchange, result);
    }

    private Repository getLocalRepository() {
        return RepositoryFactory.of(endpoint);
    }

    private void updateExchange(Exchange exchange, Object body) {
        exchange.getMessage().setBody(body);
    }
}
