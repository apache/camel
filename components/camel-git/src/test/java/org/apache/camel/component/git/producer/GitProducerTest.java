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
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.git.GitConstants;
import org.apache.camel.component.git.GitTestSupport;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GitProducerTest extends GitTestSupport {

    @Test
    public void cloneTest() {
        template.sendBody("direct:clone", "");
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
    }

    @Test
    public void initTest() {
        template.sendBody("direct:init", "");
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
    }

    @Test
    public void checkoutTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();

        // Test camel-git checkout
        template.sendBody("direct:checkout", "");

        // Check
        List<Ref> ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + branchTest)) {
                branchCreated = true;
            }
        }
        assertEquals(true, branchCreated);
        git.close();
    }

    @Test
    public void checkoutSpecificTagTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();

        // Test camel-git create tag
        template.sendBody("direct:create-tag", "");

        // Check
        List<Ref> ref = git.tagList().call();
        boolean tagCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/tags/" + tagTest)) {
                tagCreated = true;
            }
        }
        assertEquals(true, tagCreated);

        // Test camel-git create-branch
        template.sendBody("direct:checkout-specific-tag", "");

        // Check
        ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + branchTest)) {
                branchCreated = true;
            }
        }
        assertEquals(true, branchCreated);
        git.close();
    }

    @Test
    public void doubleCloneOperationTest() {
        template.sendBody("direct:clone", "");

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:clone", ""));

        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
    }

    @Test
    public void pullTest() {
        template.sendBody("direct:clone", "");
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        PullResult pr = template.requestBody("direct:pull", "", PullResult.class);
        assertTrue(pr.isSuccessful());
    }

    @Test
    public void addTest() throws Exception {
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();

        // Test camel-git add
        template.sendBodyAndHeader("direct:add", "", GitConstants.GIT_FILE_NAME, filenameToAdd);

        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.close();
    }

    @Test
    public void removeTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));

        // Test camel-git remove
        template.sendBodyAndHeader("direct:remove", "", GitConstants.GIT_FILE_NAME, filenameToAdd);

        // Check
        gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        git.commit().setMessage(commitMessage).call();
        validateGitLogs(git, commitMessage);
        status = git.status().call();
        assertFalse(status.getAdded().contains(filenameToAdd));
        git.close();
    }

    @Test
    public void commitTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));

        // Test camel-git commit
        template.sendBodyAndHeader("direct:commit", "", GitConstants.GIT_COMMIT_MESSAGE, commitMessage);

        // Check
        validateGitLogs(git, commitMessage);
        git.close();
    }

    @Test
    public void commitTestEmpty() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();

        // Test camel-git commit (with no changes)
        template.requestBodyAndHeader("direct:commit", "", GitConstants.GIT_COMMIT_MESSAGE, commitMessage);

        // Check that it has been commited twice
        validateGitLogs(git, commitMessage, commitMessage);
        git.close();
    }

    @Test
    public void commitTestAllowEmptyFalse() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();

        // Test camel-git commit (with allowEmpty set to false)
        Map<String, Object> headers = new HashMap<>();
        headers.put(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
        assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeaders("direct:commit-not-allow-empty", "", headers));

        // Check : An exception should have been raised
    }

    @Test
    public void addAndStatusAndCommitTest() throws Exception {
        // Initialize repository using JGit
        Repository repository = getTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Git git = new Git(repository);
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        // Checking camel route
        Map<String, Object> headers = new HashMap<>();
        headers.put(GitConstants.GIT_FILE_NAME, filenameToAdd);
        headers.put(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
        template.requestBodyAndHeaders("direct:add-status-commit", "", headers);
        validateGitLogs(git, commitMessage);
        git.close();
    }

    @Test
    public void commitBranchTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        validateGitLogs(git, commitMessage);
        git.checkout().setCreateBranch(true).setName(branchTest).setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM).call();

        // Test camel-git commit (with branch)
        template.send("direct:commit-branch", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessageBranch);
            }
        });
        validateGitLogs(git, commitMessageBranch, commitMessage);
        git.close();
    }

    @Test
    public void commitAllTest() throws Exception {
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();

        template.send("direct:commit-all", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessageAll);
            }
        });
        validateGitLogs(git, commitMessageAll);
        git.close();
    }

    @Test
    public void commitAllDifferentBranchTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        validateGitLogs(git, commitMessage);
        git.checkout().setCreateBranch(true).setName(branchTest).setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM).call();
        File fileToAdd1 = new File(gitLocalRepo, filenameBranchToAdd);
        fileToAdd1.createNewFile();

        // Test camel-git add and commit (different branches)
        template.send("direct:add-on-branch", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameBranchToAdd);
            }
        });

        template.send("direct:commit-all-branch", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessageAll);
            }
        });

        // Check
        validateGitLogs(git, commitMessageAll, commitMessage);
        git.close();
    }

    @Test
    public void removeFileBranchTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        validateGitLogs(git, commitMessage);
        git.checkout().setCreateBranch(true).setName(branchTest).setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM).call();
        File fileToAdd1 = new File(gitLocalRepo, filenameBranchToAdd);
        fileToAdd1.createNewFile();
        git.add().addFilepattern(filenameBranchToAdd).call();
        git.commit().setMessage(commitMessageAll).call();
        validateGitLogs(git, commitMessageAll, commitMessage);

        // Test camel-git remove
        template.send("direct:remove-on-branch", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });

        // Check
        git.checkout().setCreateBranch(false).setName(branchTest).call();
        status = git.status().call();
        assertFalse(status.getAdded().contains(filenameToAdd));
        git.close();
    }

    @Test
    public void createBranchTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();

        // Test camel-git create-branch
        template.sendBody("direct:create-branch", "");

        // Check
        List<Ref> ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + branchTest)) {
                branchCreated = true;
            }
        }
        assertEquals(true, branchCreated);
        git.close();
    }

    @Test
    public void deleteBranchTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        git.branchCreate().setName(branchTest).call();
        List<Ref> ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + branchTest)) {
                branchCreated = true;
            }
        }
        assertEquals(true, branchCreated);

        // Test camel-git delete-branch
        template.sendBody("direct:delete-branch", "");

        ref = git.branchList().call();
        branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + branchTest)) {
                branchCreated = true;
            }
        }
        assertEquals(false, branchCreated);
        git.close();
    }

    @Test
    public void statusTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());

        // Test camel-git status
        Status status = template.requestBody("direct:status", "", Status.class);

        // Check
        assertTrue(status.getAdded().contains(filenameToAdd));
        Status gitStatus = git.status().call();
        assertEquals(gitStatus.getAdded(), status.getAdded());
        git.close();
    }

    @Test
    public void statusBranchTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        git.branchCreate().setName(branchTest).call();
        List<Ref> ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + branchTest)) {
                branchCreated = true;
            }
        }
        assertEquals(true, branchCreated);
        File fileToAddDifferent = new File(gitLocalRepo, filenameBranchToAdd);
        fileToAddDifferent.createNewFile();
        git.add().addFilepattern(filenameBranchToAdd).call();

        // Test camel-git status branch
        status = template.requestBody("direct:status-branch", "", Status.class);

        // Check
        assertTrue(status.getAdded().contains(filenameBranchToAdd));
        Status gitStatus = git.status().call();
        assertEquals(gitStatus.getAdded(), status.getAdded());

        git.close();
    }

    @Test
    public void logTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();

        // Test camel-git log
        Iterable<RevCommit> revCommits = template.requestBody("direct:log", "", Iterable.class);

        // Check
        Iterator<RevCommit> gitLogs = git.log().call().iterator();
        for (RevCommit rev : revCommits) {
            RevCommit gitRevCommit = gitLogs.next();
            assertEquals(gitRevCommit.getName(), rev.getName());
        }

        git.close();
    }

    @Test
    public void logBranchTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        git.branchCreate().setName(branchTest).call();
        List<Ref> ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + branchTest)) {
                branchCreated = true;
            }
        }
        assertEquals(true, branchCreated);
        File fileToAddDifferent = new File(gitLocalRepo, filenameBranchToAdd);
        fileToAddDifferent.createNewFile();
        git.add().addFilepattern(filenameBranchToAdd).call();
        git.commit().setMessage(commitMessageAll).call();

        // Test camel-git log (with branches)
        Iterable<RevCommit> revCommits = template.requestBody("direct:log-branch", "", Iterable.class);

        // Check
        Iterator<RevCommit> gitLogs = git.log().call().iterator();
        for (RevCommit rev : revCommits) {
            RevCommit gitRevCommit = gitLogs.next();
            assertEquals(gitRevCommit.getName(), rev.getName());
            assertEquals(gitRevCommit.getShortMessage(), rev.getShortMessage());
        }
        git.close();
    }

    @Test
    public void createTagTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();

        // Test camel-git create tag
        template.sendBody("direct:create-tag", "");

        // Check
        List<Ref> ref = git.tagList().call();
        boolean tagCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/tags/" + tagTest)) {
                tagCreated = true;
            }
        }
        assertEquals(true, tagCreated);
        git.close();
    }

    @Test
    public void deleteTagTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        git.tag().setName(tagTest).call();
        List<Ref> ref = git.tagList().call();
        boolean tagCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/tags/" + tagTest)) {
                tagCreated = true;
            }
        }
        assertEquals(true, tagCreated);

        // Test camel-git delete-tag
        template.sendBody("direct:delete-tag", "");

        // Check
        ref = git.tagList().call();
        boolean tagExists = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/tags/" + tagTest)) {
                tagExists = true;
            }
        }
        assertEquals(false, tagExists);
        git.close();
    }

    @Test
    public void showBranchesTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        git.branchCreate().setName(branchTest).call();

        // Test camel-git show-branches
        List<Ref> branches = template.requestBody("direct:show-branches", "", List.class);

        // Check
        boolean branchExists = false;
        for (Ref reference : branches) {
            if (("refs/heads/" + branchTest).equals(reference.getName())) {
                branchExists = true;
            }
        }
        assertTrue(branchExists);
        git.close();
    }

    @Test
    public void cherryPickTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        git.branchCreate().setName(branchTest).call();
        List<Ref> branches = git.branchList().call();
        boolean branchExists = false;
        for (Ref reference : branches) {
            if (("refs/heads/" + branchTest).equals(reference.getName())) {
                branchExists = true;
            }
        }
        assertTrue(branchExists);
        String fileToAdd1Name = "filetest1test.txt";
        File fileToAdd1 = new File(gitLocalRepo, fileToAdd1Name);
        fileToAdd1.createNewFile();
        git.add().addFilepattern(fileToAdd1Name).call();
        status = git.status().call();
        assertTrue(status.getAdded().contains(fileToAdd1Name));
        git.commit().setMessage("Test second commit").call();
        Iterable<RevCommit> logs = git.log().call();
        validateGitLogs(git, "Test second commit", commitMessage);
        String id = logs.iterator().next().getName();

        // Test camel-git cherry-pick
        template.sendBodyAndHeader("direct:cherrypick", "", GitConstants.GIT_COMMIT_ID, id);

        // Check
        validateGitLogs(git, "Test second commit", commitMessage);
        git.close();
    }

    @Test
    public void cherryPickBranchToMasterTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        git.branchCreate().setName(branchTest).call();
        List<Ref> branches = git.branchList().call();
        boolean branchExists = false;
        for (Ref reference : branches) {
            if (("refs/heads/" + branchTest).equals(reference.getName())) {
                branchExists = true;
            }
        }
        assertTrue(branchExists);
        String fileToAdd1Name = "filetest1test.txt";
        File fileToAdd1 = new File(gitLocalRepo, fileToAdd1Name);
        fileToAdd1.createNewFile();
        git.add().addFilepattern(fileToAdd1Name).call();
        status = git.status().call();
        assertTrue(status.getAdded().contains(fileToAdd1Name));
        git.commit().setMessage("Test second commit").call();
        Iterable<RevCommit> logs = git.log().call();
        validateGitLogs(git, "Test second commit", commitMessage);
        String id = logs.iterator().next().getName();

        // Test camel-git cherry-pick (on master)
        template.sendBodyAndHeader("direct:cherrypick-master", "", GitConstants.GIT_COMMIT_ID, id);

        // Check
        git.checkout().setCreateBranch(false).setName("refs/heads/master").call();
        validateGitLogs(git, "Test second commit", commitMessage);
        git.close();
    }

    @Test
    public void remoteAddTest() throws Exception {
        Repository repository = getTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Git git = new Git(repository);
        List<RemoteConfig> remoteConfigList = git.remoteList().call();
        assertEquals(0, remoteConfigList.size());
        Object result = template.requestBody("direct:remoteAdd", "");
        assertTrue(result instanceof RemoteConfig);
        RemoteConfig remoteConfig = (RemoteConfig) result;
        remoteConfigList = git.remoteList().call();
        assertEquals(1, remoteConfigList.size());
        assertEquals(remoteConfigList.get(0).getName(), remoteConfig.getName());
        assertEquals(remoteConfigList.get(0).getURIs(), remoteConfig.getURIs());
        git.close();
    }

    @Test
    public void remoteListTest() throws Exception {
        Repository repository = getTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Git git = new Git(repository);
        RemoteAddCommand remoteAddCommand = git.remoteAdd();
        remoteAddCommand.setName("origin");
        remoteAddCommand.setUri(new URIish(remoteUriTest));
        remoteAddCommand.call();
        List<RemoteConfig> gitRemoteConfigs = git.remoteList().call();
        Object result = template.requestBody("direct:remoteList", "");
        assertTrue(result instanceof List);
        List<RemoteConfig> remoteConfigs = (List<RemoteConfig>) result;
        assertEquals(gitRemoteConfigs.size(), remoteConfigs.size());
        assertEquals(gitRemoteConfigs.get(0).getName(), remoteConfigs.get(0).getName());
        assertEquals(gitRemoteConfigs.get(0).getURIs(), remoteConfigs.get(0).getURIs());
        git.close();
    }

    @Test
    public void cleanTest() throws Exception {
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();

        // Test camel-git add
        Set<String> cleaned
                = template.requestBodyAndHeader("direct:clean", "", GitConstants.GIT_FILE_NAME, filenameToAdd, Set.class);

        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        assertTrue(cleaned.contains(filenameToAdd));
        git.close();
    }

    @Test
    public void gcTest() throws Exception {
        Git git = getGitTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();

        // Test camel-git commit (with no changes)
        template.requestBodyAndHeader("direct:commit", "", GitConstants.GIT_COMMIT_MESSAGE, commitMessage);

        // Check that it has been commited twice
        validateGitLogs(git, commitMessage, commitMessage);

        // Test camel-git add
        Properties gcResult
                = template.requestBodyAndHeader("direct:gc", "", GitConstants.GIT_FILE_NAME, filenameToAdd, Properties.class);

        assertNotNull(gcResult);
        git.close();
    }

    @Test
    public void mergeTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        validateGitLogs(git, commitMessage);
        git.checkout().setCreateBranch(true).setName(branchTest).setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM).call();

        // Test camel-git commit (with branch)
        template.send("direct:commit-branch", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessageBranch);
            }
        });
        validateGitLogs(git, commitMessageBranch, commitMessage);

        // Test camel-git commit (with branch)
        MergeResult result = template.requestBody("direct:merge", "", MergeResult.class);
        assertEquals("Fast-forward", result.getMergeStatus().toString());
        git.close();
    }

    @Test
    public void showTagsTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();

        // Test camel-git create tag
        template.sendBody("direct:create-tag", "");

        // Check
        List<Ref> result = template.requestBody("direct:show-tags", "", List.class);
        boolean tagCreated = false;
        for (Ref refInternal : result) {
            if (refInternal.getName().equals("refs/tags/" + tagTest)) {
                tagCreated = true;
            }
        }
        assertEquals(true, tagCreated);
        git.close();
    }

    @Test
    public void mergeTargetBranchTest() throws Exception {
        // Init
        Git git = getGitTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(true, gitDir.exists());
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();

        template.sendBody("direct:create-targetBranch", ""); //create target branch
        template.sendBody("direct:checkout", ""); //checkout test branch

        //add file to test branch and commit
        fileToAdd = new File(gitLocalRepo, filenameBranchToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameBranchToAdd).call();
        status = git.status().call();
        assertTrue(status.getAdded().contains(filenameBranchToAdd));
        git.commit().setMessage(commitMessageMergeBranch).call();

        //merge test branch into target branch
        MergeResult result = template.requestBody("direct:merge-targetBranch", "", MergeResult.class);
        assertEquals("Fast-forward", result.getMergeStatus().toString());
        assertTrue(Files.exists(fileToAdd.toPath())); //file exists in target branch, so merge was successful
        git.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:clone").to("git://" + gitLocalRepo
                                        + "?remotePath=https://github.com/oscerd/json-webserver-example.git&operation=clone");
                from("direct:init").to("git://" + gitLocalRepo + "?operation=init");
                from("direct:add").to("git://" + gitLocalRepo + "?operation=add");
                from("direct:checkout").to("git://" + gitLocalRepo + "?operation=checkout&branchName=" + branchTest);
                from("direct:checkout-specific-tag")
                        .to("git://" + gitLocalRepo + "?operation=checkout&branchName=" + branchTest + "&tagName=" + tagTest);
                from("direct:remove").to("git://" + gitLocalRepo + "?operation=remove");
                from("direct:add-on-branch").to("git://" + gitLocalRepo + "?operation=add&branchName=" + branchTest);
                from("direct:remove-on-branch").to("git://" + gitLocalRepo + "?operation=add&branchName=" + branchTest);
                from("direct:commit").to("git://" + gitLocalRepo + "?operation=commit");
                from("direct:commit-not-allow-empty").to("git://" + gitLocalRepo + "?operation=commit&allowEmpty=false");
                from("direct:commit-branch").to("git://" + gitLocalRepo + "?operation=commit&branchName=" + branchTest);
                from("direct:commit-all").to("git://" + gitLocalRepo + "?operation=commit");
                from("direct:commit-all-branch").to("git://" + gitLocalRepo + "?operation=commit&branchName=" + branchTest);
                from("direct:add-status-commit").to("git://" + gitLocalRepo + "?operation=add")
                        .to("git://" + gitLocalRepo + "?operation=status")
                        .choice()
                            .when(simple("${body.hasUncommittedChanges()}")).log("Commiting changes...")
                                .to("git://" + gitLocalRepo + "?operation=commit")
                            .otherwise()
                                .log("Nothing to commit")
                        .end();
                from("direct:create-branch").to("git://" + gitLocalRepo + "?operation=createBranch&branchName=" + branchTest);
                from("direct:delete-branch").to("git://" + gitLocalRepo + "?operation=deleteBranch&branchName=" + branchTest);
                from("direct:status").to("git://" + gitLocalRepo + "?operation=status");
                from("direct:status-branch").to("git://" + gitLocalRepo + "?operation=status&branchName=" + branchTest);
                from("direct:log").to("git://" + gitLocalRepo + "?operation=log");
                from("direct:log-branch").to("git://" + gitLocalRepo + "?operation=log&branchName=" + branchTest);
                from("direct:create-tag").to("git://" + gitLocalRepo + "?operation=createTag&tagName=" + tagTest);
                from("direct:delete-tag").to("git://" + gitLocalRepo + "?operation=deleteTag&tagName=" + tagTest);
                from("direct:show-branches").to("git://" + gitLocalRepo + "?operation=showBranches");
                from("direct:cherrypick").to("git://" + gitLocalRepo + "?operation=cherryPick&branchName=" + branchTest);
                from("direct:cherrypick-master")
                        .to("git://" + gitLocalRepo + "?operation=cherryPick&branchName=refs/heads/master");
                from("direct:pull").to("git://" + gitLocalRepo + "?remoteName=origin&operation=pull");
                from("direct:clean").to("git://" + gitLocalRepo + "?operation=clean");
                from("direct:gc").to("git://" + gitLocalRepo + "?operation=gc");
                from("direct:remoteAdd")
                        .to("git://" + gitLocalRepo
                            + "?operation=remoteAdd&remotePath=https://github.com/oscerd/json-webserver-example.git&remoteName=origin");
                from("direct:remoteList").to("git://" + gitLocalRepo + "?operation=remoteList");
                from("direct:merge").to("git://" + gitLocalRepo + "?operation=merge&branchName=" + branchTest);
                from("direct:show-tags").to("git://" + gitLocalRepo + "?operation=showTags");
                from("direct:create-targetBranch")
                        .to("git://" + gitLocalRepo + "?operation=createBranch&branchName=" + targetBranchTest);
                from("direct:merge-targetBranch").to("git://" + gitLocalRepo + "?operation=merge&targetBranchName="
                                                     + targetBranchTest + "&branchName=" + branchTest);
            }
        };
    }

}
