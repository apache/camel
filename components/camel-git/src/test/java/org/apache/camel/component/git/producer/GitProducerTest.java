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
import java.io.IOException;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.git.GitConstants;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.junit.Ignore;
import org.junit.Test;

public class GitProducerTest extends GitTestSupport {
    
    @Test
    public void cloneTest() throws Exception {
        template.sendBody("direct:clone","");
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
    }
    
    @Test
    public void initTest() throws Exception {
        template.sendBody("direct:init","");
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
    }
    
    @Test
    public void addTest() throws Exception {

    	Repository repository = getTestRepository();
        
        File fileToAdd = new File(GIT_LOCAL_REPO, FILENAME_TO_ADD);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(FILENAME_TO_ADD));
        repository.close();
    }
    
    @Test
    public void removeTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(GIT_LOCAL_REPO, FILENAME_TO_ADD);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(FILENAME_TO_ADD));
        
        template.send("direct:remove", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE);
            }
        });
        Iterable<RevCommit> logs = new Git(repository).log()
                .call();
        int count = 0;
        for (RevCommit rev : logs) {
            assertEquals(rev.getShortMessage(), COMMIT_MESSAGE);
            count++;
        }
        assertEquals(count, 1);
        
        status = new Git(repository).status().call();

        assertFalse(status.getAdded().contains(FILENAME_TO_ADD));
        
        repository.close();
    }
    
    @Test
    public void commitTest() throws Exception {

    	Repository repository = getTestRepository();
        
        File fileToAdd = new File(GIT_LOCAL_REPO, FILENAME_TO_ADD);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(FILENAME_TO_ADD));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE);
            }
        });
        Iterable<RevCommit> logs = new Git(repository).log()
                .call();
        int count = 0;
        for (RevCommit rev : logs) {
            assertEquals(rev.getShortMessage(), COMMIT_MESSAGE);
            count++;
        }
        assertEquals(count, 1);
        repository.close();
    }
    
    @Test
    public void commitBranchTest() throws Exception {

    	Repository repository = getTestRepository();
        
        File fileToAdd = new File(GIT_LOCAL_REPO, FILENAME_TO_ADD);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(FILENAME_TO_ADD));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE);
            }
        });
        Iterable<RevCommit> logs = new Git(repository).log()
                .call();
        int count = 0;
        for (RevCommit rev : logs) {
            assertEquals(rev.getShortMessage(), COMMIT_MESSAGE);
            count++;
        }
        assertEquals(count, 1);
        
        Git git = new Git(repository);
        git.checkout().setCreateBranch(true).setName(BRANCH_TEST).
        setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM).call();
        
        template.send("direct:commit-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE_BRANCH);
            }
        });
        logs = git.log().call();
        count = 0;
        for (RevCommit rev : logs) {
        	if (count == 0) assertEquals(rev.getShortMessage(), COMMIT_MESSAGE_BRANCH);
        	if (count == 1) assertEquals(rev.getShortMessage(), COMMIT_MESSAGE);
            count++;
        }
        assertEquals(count, 2);
        repository.close();
    }
    

    
    @Test
    public void commitAllTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(GIT_LOCAL_REPO, FILENAME_TO_ADD);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        
        template.send("direct:commit-all", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE_ALL);
            }
        });
        Iterable<RevCommit> logs = new Git(repository).log()
                .call();
        int count = 0;
        for (RevCommit rev : logs) {
            assertEquals(rev.getShortMessage(), COMMIT_MESSAGE_ALL);
            count++;
        }
        assertEquals(count, 1);
        repository.close();
    }
    
    @Test
    public void commitAllDifferentBranchTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(GIT_LOCAL_REPO, FILENAME_TO_ADD);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(FILENAME_TO_ADD));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE);
            }
        });
        Iterable<RevCommit> logs = new Git(repository).log()
                .call();
        int count = 0;
        for (RevCommit rev : logs) {
            assertEquals(rev.getShortMessage(), COMMIT_MESSAGE);
            count++;
        }
        assertEquals(count, 1);
        
        Git git = new Git(repository);
        git.checkout().setCreateBranch(true).setName(BRANCH_TEST).
        setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM).call();
        
        File fileToAdd1 = new File(GIT_LOCAL_REPO, FILENAME_BRANCH_TO_ADD);
        fileToAdd1.createNewFile();
        
        template.send("direct:add-on-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_BRANCH_TO_ADD);
            }
        });
        
        template.send("direct:commit-all-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE_ALL);
            }
        });
        logs = git.log().call();
        count = 0;
        for (RevCommit rev : logs) {
            if (count == 0) assertEquals(rev.getShortMessage(), COMMIT_MESSAGE_ALL);
            if (count == 1) assertEquals(rev.getShortMessage(), COMMIT_MESSAGE);
            count++;
        }
        assertEquals(count, 2);
        repository.close();
    }
    
    @Test
    public void removeFileBranchTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(GIT_LOCAL_REPO, FILENAME_TO_ADD);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(FILENAME_TO_ADD));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE);
            }
        });
        Iterable<RevCommit> logs = new Git(repository).log()
                .call();
        int count = 0;
        for (RevCommit rev : logs) {
            assertEquals(rev.getShortMessage(), COMMIT_MESSAGE);
            count++;
        }
        assertEquals(count, 1);
        
        Git git = new Git(repository);
        git.checkout().setCreateBranch(true).setName(BRANCH_TEST).
        setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM).call();
        
        File fileToAdd1 = new File(GIT_LOCAL_REPO, FILENAME_BRANCH_TO_ADD);
        fileToAdd1.createNewFile();
        
        template.send("direct:add-on-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_BRANCH_TO_ADD);
            }
        });
        
        template.send("direct:commit-all-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE_ALL);
            }
        });
        logs = git.log().call();
        count = 0;
        for (RevCommit rev : logs) {
            if (count == 0) assertEquals(rev.getShortMessage(), COMMIT_MESSAGE_ALL);
            if (count == 1) assertEquals(rev.getShortMessage(), COMMIT_MESSAGE);
            count++;
        }
        assertEquals(count, 2);
        
        template.send("direct:remove-on-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        
        git = new Git(repository);
        git.checkout().setCreateBranch(false).setName(BRANCH_TEST).call();
        
        status = git.status().call();
        assertFalse(status.getAdded().contains(FILENAME_TO_ADD));
        
        repository.close();
    }
    
    @Test
    public void createBranchTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(GIT_LOCAL_REPO, FILENAME_TO_ADD);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(FILENAME_TO_ADD));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE);
            }
        });
        
        Git git = new Git(repository);
        
        template.sendBody("direct:create-branch", "");
        
        List<Ref> ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + BRANCH_TEST)) {
                branchCreated = true;
            }
        }
        assertEquals(branchCreated, true);
        repository.close();
    }
    
    @Test
    public void deleteBranchTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(GIT_LOCAL_REPO, FILENAME_TO_ADD);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(FILENAME_TO_ADD));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE);
            }
        });
        
        Git git = new Git(repository);
        
        template.sendBody("direct:create-branch", "");
        
        List<Ref> ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + BRANCH_TEST)) {
                branchCreated = true;
            }
        }
        assertEquals(branchCreated, true);
        
        template.sendBody("direct:delete-branch", "");
        
        ref = git.branchList().call();
        branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + BRANCH_TEST)) {
                branchCreated = true;
            }
        }
        assertEquals(branchCreated, false);
        repository.close();
    }
    
    @Test
    public void statusTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(GIT_LOCAL_REPO, FILENAME_TO_ADD);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = template.requestBody("direct:status", "", Status.class);
        assertTrue(status.getAdded().contains(FILENAME_TO_ADD));
        
        repository.close();
    }
    
    @Test
    public void statusBranchTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(GIT_LOCAL_REPO, FILENAME_TO_ADD);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = template.requestBody("direct:status", "", Status.class);
        assertTrue(status.getAdded().contains(FILENAME_TO_ADD));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE);
            }
        });
        
        template.sendBody("direct:create-branch", "");
        
        Git git = new Git(repository);
        
        List<Ref> ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + BRANCH_TEST)) {
                branchCreated = true;
            }
        }
        assertEquals(branchCreated, true);
        
        File fileToAddDifferent = new File(GIT_LOCAL_REPO, FILENAME_BRANCH_TO_ADD);
        fileToAddDifferent.createNewFile();
        
        template.send("direct:add-on-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_BRANCH_TO_ADD);
            }
        });
        
        status = template.requestBody("direct:status-branch", "", Status.class);
        assertTrue(status.getAdded().contains(FILENAME_BRANCH_TO_ADD));
        
        repository.close();
    }
    
    @Test
    public void logTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(GIT_LOCAL_REPO, FILENAME_TO_ADD);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = template.requestBody("direct:status", "", Status.class);
        assertTrue(status.getAdded().contains(FILENAME_TO_ADD));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE);
            }
        });
        
        Iterable<RevCommit> revCommits = template.requestBody("direct:log", "", Iterable.class);
        for (RevCommit rev : revCommits) {
            assertEquals(rev.getShortMessage(), COMMIT_MESSAGE);
        }        
        repository.close();
    }
    
    @Test
    public void logBranchTest() throws Exception {

        Repository repository = getTestRepository();
        
        File fileToAdd = new File(GIT_LOCAL_REPO, FILENAME_TO_ADD);
        fileToAdd.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_TO_ADD);
            }
        });
        File gitDir = new File(GIT_LOCAL_REPO, ".git");
        assertEquals(gitDir.exists(), true);
        
        Status status = template.requestBody("direct:status", "", Status.class);
        assertTrue(status.getAdded().contains(FILENAME_TO_ADD));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE);
            }
        });
        
        Iterable<RevCommit> revCommits = template.requestBody("direct:log", "", Iterable.class);
        for (RevCommit rev : revCommits) {
            assertEquals(rev.getShortMessage(), COMMIT_MESSAGE);
        }
        
        template.sendBody("direct:create-branch", "");
        
        Git git = new Git(repository);
        
        List<Ref> ref = git.branchList().call();
        boolean branchCreated = false;
        for (Ref refInternal : ref) {
            if (refInternal.getName().equals("refs/heads/" + BRANCH_TEST)) {
                branchCreated = true;
            }
        }
        assertEquals(branchCreated, true);
        
        File fileToAddDifferent = new File(GIT_LOCAL_REPO, FILENAME_BRANCH_TO_ADD);
        fileToAddDifferent.createNewFile();
        
        template.send("direct:add-on-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, FILENAME_BRANCH_TO_ADD);
            }
        });
        
        template.send("direct:commit-all-branch", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, COMMIT_MESSAGE_ALL);
            }
        });
        
        revCommits = template.requestBody("direct:log-branch", "", Iterable.class);
        int count = 0;
        for (RevCommit rev : revCommits) {
            if (count == 0) assertEquals(rev.getShortMessage(), COMMIT_MESSAGE_ALL);
            if (count == 1) assertEquals(rev.getShortMessage(), COMMIT_MESSAGE);
            count++;
        }
        
        repository.close();
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {            
            @Override
            public void configure() throws Exception {
                from("direct:clone")
                        .to("git://" + GIT_LOCAL_REPO + "?remotePath=https://github.com/oscerd/json-webserver-example.git&operation=clone");
                from("direct:init")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=init");
                from("direct:add")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=add");
                from("direct:remove")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=rm");
                from("direct:add-on-branch")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=add&branchName=" + BRANCH_TEST);
                from("direct:remove-on-branch")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=add&branchName=" + BRANCH_TEST);
                from("direct:commit")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=commit");
                from("direct:commit-branch")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=commit&branchName=" + BRANCH_TEST);
                from("direct:commit-all")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=commit");
                from("direct:commit-all-branch")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=commit&branchName=" + BRANCH_TEST);
                from("direct:create-branch")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=createBranch&branchName=" + BRANCH_TEST);
                from("direct:delete-branch")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=deleteBranch&branchName=" + BRANCH_TEST);
                from("direct:status")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=status");
                from("direct:status-branch")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=status&branchName=" + BRANCH_TEST);
                from("direct:log")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=log");
                from("direct:log-branch")
                        .to("git://" + GIT_LOCAL_REPO + "?operation=log&branchName=" + BRANCH_TEST);
            } 
        };
    }

}
