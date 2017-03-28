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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.git.GitTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class GitConsumerTest extends GitTestSupport {

    @Test
    public void commitConsumerTest() throws Exception {
        // Init
        MockEndpoint mockResultCommit = getMockEndpoint("mock:result-commit");
        mockResultCommit.expectedMessageCount(2);
        Git git = getGitTestRepository();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        File fileToAdd1 = new File(gitLocalRepo, filenameBranchToAdd);
        fileToAdd1.createNewFile();
        git.add().addFilepattern(filenameBranchToAdd).call();
        status = git.status().call();
        assertTrue(status.getAdded().contains(filenameBranchToAdd));
        git.commit().setMessage("Test test Commit").call();
        Iterable<RevCommit> logs = git.log().call();
        validateGitLogs(git, "Test test Commit", commitMessage);
        // Test
        mockResultCommit.assertIsSatisfied();

        // Check
        Exchange ex1 = mockResultCommit.getExchanges().get(0);
        Exchange ex2 = mockResultCommit.getExchanges().get(1);
        assertEquals(commitMessage, ex2.getOut().getBody(RevCommit.class).getShortMessage());
        assertEquals("Test test Commit", ex1.getOut().getBody(RevCommit.class).getShortMessage());
        git.close();
    }

    @Test
    public void tagConsumerTest() throws Exception {
        // Init
        MockEndpoint mockResultTag = getMockEndpoint("mock:result-tag");
        mockResultTag.expectedMessageCount(1);
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
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
        assertEquals(tagCreated, true);

        // Test
        mockResultTag.assertIsSatisfied();

        // Check
        Exchange exchange = mockResultTag.getExchanges().get(0);
        assertEquals("refs/tags/" + tagTest, exchange.getOut().getBody(ObjectIdRef.Unpeeled.class).getName());
        git.close();
    }

    @Test
    public void branchConsumerTest() throws Exception {

        // Init
        MockEndpoint mockResultBranch = getMockEndpoint("mock:result-branch");
        mockResultBranch.expectedMessageCount(2);
        Git git = getGitTestRepository();
        File fileToAdd = new File(gitLocalRepo, filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(gitLocalRepo, ".git");
        assertEquals(gitDir.exists(), true);
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
        assertEquals(branchCreated, true);

        // Test
        mockResultBranch.assertIsSatisfied();

        // Check
        List<Exchange> exchanges = mockResultBranch.getExchanges();
        assertEquals("refs/heads/master", exchanges.get(0).getOut().getBody(ObjectIdRef.Unpeeled.class).getName());
        assertEquals("refs/heads/" + branchTest, exchanges.get(1).getOut().getBody(ObjectIdRef.Unpeeled.class).getName());

        git.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:clone").to("git://" + gitLocalRepo + "?remotePath=https://github.com/oscerd/json-webserver-example.git&operation=clone");
                from("direct:init").to("git://" + gitLocalRepo + "?operation=init");
                from("direct:add").to("git://" + gitLocalRepo + "?operation=add");
                from("direct:commit").to("git://" + gitLocalRepo + "?operation=commit");
                from("direct:create-branch").to("git://" + gitLocalRepo + "?operation=createBranch&branchName=" + branchTest);
                from("direct:create-tag").to("git://" + gitLocalRepo + "?operation=createTag&tagName=" + tagTest);
                from("git://" + gitLocalRepo + "?type=commit").to("mock:result-commit");
                from("git://" + gitLocalRepo + "?type=tag").to("mock:result-tag");
                from("git://" + gitLocalRepo + "?type=branch").to("mock:result-branch");
            }
        };
    }

}
