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
package org.apache.camel.example.jira;

import com.atlassian.jira.rest.client.api.domain.Comment;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NewCommentsRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(NewCommentsRoute.class);

    @Override
    public void configure() {

        LOG.info(" >>>>>>>>>>>>>>>>>>>>> jira example - retrieve only new comments");
        // change the fields accordinly to your target jira server
        from("jira://newComments?jql=RAW(project=COM AND resolution = Unresolved)&delay=4000")
                .process(exchange -> {
                    Comment comment = (Comment) exchange.getIn().getBody();
                    LOG.info("new jira comment id: {} - by: {}: {}", comment.getId(), comment.getAuthor().getDisplayName(),
                            comment.getBody());
                })
                .to("mock:result");

    }

}
