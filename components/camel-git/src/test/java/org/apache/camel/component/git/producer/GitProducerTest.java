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
package org.apache.camel.component.git.producer;

import java.io.File;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.git.GitConstants;
import org.apache.camel.component.git.GitTestSupport;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class GitProducerTest extends GitTestSupport {
    
    @Test
    public void cloneTest() throws Exception {
        template.sendBody("direct:clone", "");
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
    }
    
    @Test
    public void initTest() throws Exception {
        template.sendBody("direct:init", "");
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
    }
    
    @Test
    public void addTest() throws Exception {

        Repository repository = getTestRepository();
       
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        repository.close();
    }
    
    @Test
    public void removeTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        template.send("direct:remove", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
            }
        });
        Iterable<RevCommit> logs = new Git(repository).log()
                .call();
        int count = 0;
        for (RevCommit rev : logs) {
            assertEquals(rev.getShortMessage(), commitMessage);
            count++;
        }
        assertEquals(count, 1);
        
        status = new Git(repository).status().call();

        assertFalse(status.getAdded().contains(filenameToAdd));
        
        repository.close();
    }
    
    @Test
    public void commitTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
            }
        });
        Iterable<RevCommit> logs = new Git(repository).log()
                .call();
        int count = 0;
        for (RevCommit rev : logs) {
            assertEquals(rev.getShortMessage(), commitMessage);
            count++;
        }
        assertEquals(count, 1);
        repository.close();
    }
    
    @Test
    public void commitBranchTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
            }
        });
        Iterable<RevCommit> logs = new Git(repository).log()
                .call();
        int count = 0;
        for (RevCommit rev : logs) {
            assertEquals(rev.getShortMessage(), commitMessage);
            count++;
        }
        assertEquals(count, 1);
        
        Git git = new Git(repository);
        git.checkout().setCreateBranch(true).setName(branchTest).
        setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM).call();
        
        template.send("direct:commit-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessageBranch);
            }
        });
        logs = git.log().call();
        count = 0;
        for (RevCommit rev : logs) {
            if (count == 0) {
                assertEquals(rev.getShortMessage(), commitMessageBranch);
            }
            if (count == 1) {
                assertEquals(rev.getShortMessage(), commitMessage);
            }
            count++;
        }
        assertEquals(count, 2);
        repository.close();
    }
    

    
    @Test
    public void commitAllTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        
        template.send("direct:commit-all", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessageAll);
            }
        });
        Iterable<RevCommit> logs = new Git(repository).log()
                .call();
        int count = 0;
        for (RevCommit rev : logs) {
            assertEquals(rev.getShortMessage(), commitMessageAll);
            count++;
        }
        assertEquals(count, 1);
        repository.close();
    }
    
    @Test
    public void commitAllDifferentBranchTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
            }
        });
        Iterable<RevCommit> logs = new Git(repository).log()
                .call();
        int count = 0;
        for (RevCommit rev : logs) {
            assertEquals(rev.getShortMessage(), commitMessage);
            count++;
        }
        assertEquals(count, 1);
        
        Git git = new Git(repository);
        git.checkout().setCreateBranch(true).setName(branchTest).
        setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM).call();
        
        File fileToAdd1 = new File(gitLocalRepo, filenameBranchToAdd);
        fileToAdd1.createNewFile();
        
        template.send("direct:add-on-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameBranchToAdd);
            }
        });
        
        template.send("direct:commit-all-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessageAll);
            }
        });
        logs = git.log().call();
        count = 0;
        for (RevCommit rev : logs) {
            if (count == 0) {
                assertEquals(rev.getShortMessage(), commitMessageAll);
            }
            if (count == 1) {
                assertEquals(rev.getShortMessage(), commitMessage);
            }
            count++;
        }
        assertEquals(count, 2);
        repository.close();
    }
    
    @Test
    public void removeFileBranchTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
            }
        });
        Iterable<RevCommit> logs = new Git(repository).log()
                .call();
        int count = 0;
        for (RevCommit rev : logs) {
            assertEquals(rev.getShortMessage(), commitMessage);
            count++;
        }
        assertEquals(count, 1);
        
        Git git = new Git(repository);
        git.checkout().setCreateBranch(true).setName(branchTest).
        setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM).call();
        
        File fileToAdd1 = new File(gitLocalRepo, filenameBranchToAdd);
        fileToAdd1.createNewFile();
        
        template.send("direct:add-on-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameBranchToAdd);
            }
        });
        
        template.send("direct:commit-all-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessageAll);
            }
        });
        logs = git.log().call();
        count = 0;
        for (RevCommit rev : logs) {
            if (count == 0) {
                assertEquals(rev.getShortMessage(), commitMessageAll);
            }
            if (count == 1) {
                assertEquals(rev.getShortMessage(), commitMessage);
            }
            count++;
        }
        assertEquals(count, 2);
        
        template.send("direct:remove-on-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        
        git = new Git(repository);
        git.checkout().setCreateBranch(false).setName(branchTest).call();
        
        status = git.status().call();
        assertFalse(status.getAdded().contains(filenameToAdd));
        
        repository.close();
    }
    
    @Test
    public void createBranchTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
            }
        });
        
        Git git = new Git(repository);
        
        template.sendBody("direct:create-branch", "");
        
        List<Ref> ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + branchTest)) {
                branchCreated = true;
            }
        }
        assertEquals(branchCreated, true);
        repository.close();
    }
    
    @Test
    public void deleteBranchTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
            }
        });
        
        Git git = new Git(repository);
        
        template.sendBody("direct:create-branch", "");
        
        List<Ref> ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + branchTest)) {
                branchCreated = true;
            }
        }
        assertEquals(branchCreated, true);
        
        template.sendBody("direct:delete-branch", "");
        
        ref = git.branchList().call();
        branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + branchTest)) {
                branchCreated = true;
            }
        }
        assertEquals(branchCreated, false);
        repository.close();
    }
    
    @Test
    public void statusTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = template.requestBody("direct:status", "", Status.class);
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        repository.close();
    }
    
    @Test
    public void statusBranchTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = template.requestBody("direct:status", "", Status.class);
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
            }
        });
        
        template.sendBody("direct:create-branch", "");
        
        Git git = new Git(repository);
        
        List<Ref> ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + branchTest)) {
                branchCreated = true;
            }
        }
        assertEquals(branchCreated, true);
        
        File fileToAddDifferent = new File(gitLocalRepo, filenameBranchToAdd);
        fileToAddDifferent.createNewFile();
        
        template.send("direct:add-on-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameBranchToAdd);
            }
        });
        
        status = template.requestBody("direct:status-branch", "", Status.class);
        assertTrue(status.getAdded().contains(filenameBranchToAdd));
        
        repository.close();
    }
    
    @Test
    public void logTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = template.requestBody("direct:status", "", Status.class);
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
            }
        });
        
        Iterable<RevCommit> revCommits = template.requestBody("direct:log", "", Iterable.class);
        for (RevCommit rev : revCommits) {
            assertEquals(rev.getShortMessage(), commitMessage);
        }        
        repository.close();
    }
    
    @Test
    public void logBranchTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = template.requestBody("direct:status", "", Status.class);
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
            }
        });
        
        Iterable<RevCommit> revCommits = template.requestBody("direct:log", "", Iterable.class);
        for (RevCommit rev : revCommits) {
            assertEquals(rev.getShortMessage(), commitMessage);
        }
        
        template.sendBody("direct:create-branch", "");
        
        Git git = new Git(repository);
        
        List<Ref> ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + branchTest)) {
                branchCreated = true;
            }
        }
        assertEquals(branchCreated, true);
        
        File fileToAddDifferent = new File(gitLocalRepo, filenameBranchToAdd);
        fileToAddDifferent.createNewFile();
        
        template.send("direct:add-on-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameBranchToAdd);
            }
        });
        
        template.send("direct:commit-all-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessageAll);
            }
        });
        
        revCommits = template.requestBody("direct:log-branch", "", Iterable.class);
        int count = 0;
        for (RevCommit rev : revCommits) {
            if (count == 0) {
                assertEquals(rev.getShortMessage(), commitMessageAll);
            }
            if (count == 1) {
                assertEquals(rev.getShortMessage(), commitMessage);
            }
            count++;
        }
        
        repository.close();
    }
    
    @Test
    public void createTagTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
            }
        });
        
        Git git = new Git(repository);
        
        template.sendBody("direct:create-tag", "");
        
        List<Ref> ref = git.tagList().call();
        boolean tagCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/tags/" + tagTest)) {
                tagCreated = true;
            }
        }
        assertEquals(tagCreated, true);
        repository.close();
    }
    
    @Test
    public void deleteTagTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
            }
        });
        
        Git git = new Git(repository);
        
        template.sendBody("direct:create-tag", "");
        
        List<Ref> ref = git.tagList().call();
        boolean tagCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/tags/" + tagTest)) {
                tagCreated = true;
            }
        }
        assertEquals(tagCreated, true);
        
        template.sendBody("direct:delete-tag", "");
        
        ref = git.tagList().call();
        boolean tagExists = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/tags/" + tagTest)) {
                tagExists = true;
            }
        }
        assertEquals(tagExists, false);
        repository.close();
    }
    
    @Test
    public void showBranchesTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameToAdd);
            }
        });
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, commitMessage);
            }
        });
        
        Git git = new Git(repository);
        
        template.sendBody("direct:create-branch", "");
        
        List<Ref> branches = template.requestBody("direct:show-branches", "", List.class);
        
        Boolean branchExists = false;
        
        for (Ref reference : branches) {
            if (("refs/heads/" + branchTest).equals(reference.getName())) {
                branchExists = true;
            }
        }
        assertTrue(branchExists);
        
        repository.close();
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {            
            @Override
            public void configure() throws Exception {
                from("direct:clone")
                        .to("git://" + gitLocalRepo + "?remotePath=https://github.com/oscerd/json-webserver-example.git&operation=clone");
                from("direct:init")
                        .to("git://" + gitLocalRepo + "?operation=init");
                from("direct:add")
                        .to("git://" + gitLocalRepo + "?operation=add");
                from("direct:remove")
                        .to("git://" + gitLocalRepo + "?operation=remove");
                from("direct:add-on-branch")
                        .to("git://" + gitLocalRepo + "?operation=add&branchName=" + branchTest);
                from("direct:remove-on-branch")
                        .to("git://" + gitLocalRepo + "?operation=add&branchName=" + branchTest);
                from("direct:commit")
                        .to("git://" + gitLocalRepo + "?operation=commit");
                from("direct:commit-branch")
                        .to("git://" + gitLocalRepo + "?operation=commit&branchName=" + branchTest);
                from("direct:commit-all")
                        .to("git://" + gitLocalRepo + "?operation=commit");
                from("direct:commit-all-branch")
                        .to("git://" + gitLocalRepo + "?operation=commit&branchName=" + branchTest);
                from("direct:create-branch")
                        .to("git://" + gitLocalRepo + "?operation=createBranch&branchName=" + branchTest);
                from("direct:delete-branch")
                        .to("git://" + gitLocalRepo + "?operation=deleteBranch&branchName=" + branchTest);
                from("direct:status")
                        .to("git://" + gitLocalRepo + "?operation=status");
                from("direct:status-branch")
                        .to("git://" + gitLocalRepo + "?operation=status&branchName=" + branchTest);
                from("direct:log")
                        .to("git://" + gitLocalRepo + "?operation=log");
                from("direct:log-branch")
                        .to("git://" + gitLocalRepo + "?operation=log&branchName=" + branchTest);
                from("direct:create-tag")
                        .to("git://" + gitLocalRepo + "?operation=createTag&tagName=" + tagTest);
                from("direct:delete-tag")
                        .to("git://" + gitLocalRepo + "?operation=deleteTag&tagName=" + tagTest);
                from("direct:show-branches")
                        .to("git://" + gitLocalRepo + "?operation=showBranches");
            } 
        };
    }

}
