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
package org.apache.camel.component.git.consumer;

import java.io.File;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.git.GitConstants;
import org.apache.camel.component.git.GitTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class GitConsumerTest extends GitTestSupport {
    
    @Test
    public void commitConsumerTest() throws Exception {

        Repository repository = getTestRepository();
        MockEndpoint added = getMockEndpoint("mock:result-commit");
        
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
        
        File fileToAdd1 = new File(gitLocalRepo, filenameBranchToAdd);
        fileToAdd1.createNewFile();
        
        template.send("direct:add", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_FILE_NAME, filenameBranchToAdd);
            }
        });
        
        status = new Git(repository).status().call();
        assertTrue(status.getAdded().contains(filenameBranchToAdd));
        
        template.send("direct:commit", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(GitConstants.GIT_COMMIT_MESSAGE, "Test test Commit");
            }
        });
        logs  = new Git(repository).log()
                .call();
        count = 0;
        for (RevCommit rev : logs) {
            count++;
        }
        assertEquals(count, 2);
        
        Thread.sleep(1 * 5000);
        Exchange ex1 = added.getExchanges().get(0);
        Exchange ex2 = added.getExchanges().get(1);
        assertEquals(commitMessage, ex2.getOut().getBody(RevCommit.class).getShortMessage());
        assertEquals("Test test Commit", ex1.getOut().getBody(RevCommit.class).getShortMessage());
        repository.close();
    }
    
    @Test
    public void tagConsumerTest() throws Exception {

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
        
        MockEndpoint added = getMockEndpoint("mock:result-tag");
        
        Thread.sleep(1 * 5000);
        assertEquals(added.getExchanges().size(), 1);
        repository.close();
    }
    
    @Test
    public void branchConsumerTest() throws Exception {

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
        
        MockEndpoint added = getMockEndpoint("mock:result-branch");
        
        Thread.sleep(1 * 5000);
        assertEquals(added.getExchanges().size(), 2);
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
                from("direct:commit")
                        .to("git://" + gitLocalRepo + "?operation=commit");
                from("direct:create-branch")
                        .to("git://" + gitLocalRepo + "?operation=createBranch&branchName=" + branchTest);
                from("direct:create-tag")
                        .to("git://" + gitLocalRepo + "?operation=createTag&tagName=" + tagTest);
                from("git://" + gitLocalRepo + "?type=commit")
                        .to("mock:result-commit");
                from("git://" + gitLocalRepo + "?type=tag")
                        .to("mock:result-tag");
                from("git://" + gitLocalRepo + "?type=branch")
                        .to("mock:result-branch");
            } 
        };
    }

}

