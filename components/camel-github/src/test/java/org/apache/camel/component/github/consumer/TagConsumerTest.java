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
package org.apache.camel.component.github.consumer;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.github.GitHubComponent;
import org.apache.camel.component.github.GitHubComponentTestBase;
import org.eclipse.egit.github.core.RepositoryTag;
import org.junit.Test;

public class TagConsumerTest extends GitHubComponentTestBase {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                context.addComponent("github", new GitHubComponent());
                from("github://tag?username=someguy&password=apassword&repoOwner=anotherguy&repoName=somerepo")
                        .process(new RepositoryTagProcessor())
                        .to(mockResultEndpoint);
            }
        };
    }


    @Test
    public void tagConsumerTest() throws Exception {
        RepositoryTag tag1 = repositoryService.addTag("TAG1");
        RepositoryTag tag2 = repositoryService.addTag("TAG2");
        RepositoryTag tag3 = repositoryService.addTag("TAG3");
        mockResultEndpoint.expectedBodiesReceivedInAnyOrder(tag1, tag2, tag3);
        Thread.sleep(1 * 1000);

        mockResultEndpoint.assertIsSatisfied();
    }

    public class RepositoryTagProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            RepositoryTag tag = (RepositoryTag) in.getBody();
            log.debug("Got TAG  [" + tag.getName() + "]");
        }
    }
}
