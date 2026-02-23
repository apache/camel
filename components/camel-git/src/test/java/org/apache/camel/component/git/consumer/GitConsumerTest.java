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
package org.apache.camel.component.git.consumer;

import java.io.File;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.git.GitTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Ref;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GitConsumerTest extends GitTestSupport {

    @Test
    public void commitConsumerNotExistingBranchTest() throws Exception {
        // Init
        MockEndpoint mockResultCommit = getMockEndpoint("mock:result-commit-notexistent");
        mockResultCommit.expectedMessageCount(0);
        Git git = getGitTestRepository();
        File gitDir = new File(getGitDir(), ".git");
        assertTrue(gitDir.exists());
        File fileToAdd = new File(getGitDir(), filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        Status status = git.status().call();
        assertTrue(status.getAdded().contains(filenameToAdd));
        git.commit().setMessage(commitMessage).call();
        File fileToAdd1 = new File(getGitDir(), filenameBranchToAdd);
        fileToAdd1.createNewFile();
        git.add().addFilepattern(filenameBranchToAdd).call();
        status = git.status().call();
        assertTrue(status.getAdded().contains(filenameBranchToAdd));
        git.commit().setMessage("Test test Commit").call();
        validateGitLogs(git, "Test test Commit", commitMessage);
        // Test
        mockResultCommit.assertIsSatisfied();
        git.close();
    }

    @Test
    public void tagConsumerTest() throws Exception {
        // Init
        MockEndpoint mockResultTag = getMockEndpoint("mock:result-tag");
        mockResultTag.expectedMessageCount(1);
        Git git = getGitTestRepository();
        File fileToAdd = new File(getGitDir(), filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(getGitDir(), ".git");
        assertTrue(gitDir.exists());
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
        assertTrue(tagCreated);

        // Test
        mockResultTag.assertIsSatisfied();

        // Check
        Exchange exchange = mockResultTag.getExchanges().get(0);
        assertEquals("refs/tags/" + tagTest, exchange.getMessage().getBody(String.class));
        git.close();
    }

    @Test
    public void branchConsumerTest() throws Exception {

        // Init
        MockEndpoint mockResultBranch = getMockEndpoint("mock:result-branch");
        mockResultBranch.expectedMessageCount(2);
        Git git = getGitTestRepository();
        File fileToAdd = new File(getGitDir(), filenameToAdd);
        fileToAdd.createNewFile();
        git.add().addFilepattern(filenameToAdd).call();
        File gitDir = new File(getGitDir(), ".git");
        assertTrue(gitDir.exists());
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
        assertTrue(branchCreated);

        // Test
        mockResultBranch.assertIsSatisfied();

        // Check
        List<Exchange> exchanges = mockResultBranch.getExchanges();
        assertEquals("refs/heads/master", exchanges.get(0).getMessage().getBody(String.class));
        assertEquals("refs/heads/" + branchTest, exchanges.get(1).getMessage().getBody(String.class));

        git.close();
    }

    @Test
    public void injectConfigFileTest() throws Exception {
        GitBranchConsumer consumer;

        consumer = (GitBranchConsumer) context.getRoute("injectConfigFileFromClasspath").getConsumer();
        assertEquals("fromClasspath", consumer.getRepository().getConfig().getString("init", null, "defaultBranch"));

        consumer = (GitBranchConsumer) context.getRoute("injectConfigFileFromHttp").getConsumer();
        assertEquals("fromHttp", consumer.getRepository().getConfig().getString("init", null, "defaultBranch"));

        consumer = (GitBranchConsumer) context.getRoute("defaultBranchTest").getConsumer();
        assertNull(consumer.getRepository().getConfig().getString("init", null, "defaultBranch"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        // force create git repo before routes
        getTestRepository();
        final String dir = getGitDir().getPath();

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("git://" + dir + "?type=commit&branchName=notexisting").to("mock:result-commit-notexistent");
                from("git://" + dir + "?type=tag").to("mock:result-tag");
                from("git://" + dir + "?type=branch&gitConfigFile=classpath:git.config")
                        .id("injectConfigFileFromClasspath")
                        .to("mock:result-branch-configfile");
                from("git://" + dir
                     + "?type=branch&gitConfigFile=https://gist.githubusercontent.com/gilvansfilho/a61f6ab811a5e8e9d46c4fba1235abc1/raw/a1f614c90e29f1cdd83534aa913f5d276beace2c/gitconfig")
                        .id("injectConfigFileFromHttp")
                        .to("mock:result-branch-configfile");
                from("git://" + dir + "?type=branch").id("defaultBranchTest").to("mock:result-branch");
            }
        };
    }

}
