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

import java.util.Date;

import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static org.apache.camel.component.jira.JiraConstants.ISSUE_PRIORITY_NAME;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_PROJECT_KEY;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_SUMMARY;
import static org.apache.camel.component.jira.JiraConstants.ISSUE_TYPE_NAME;

@Component
public class AddIssueRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(AddIssueRoute.class);

    @Override
    public void configure() {

        LOG.info(" >>>>>>>>>>>>>>>>>>>>> jira example - add new issue");
        // change the fields accordinly to your target jira server
        from("timer://foo?fixedRate=true&period=50000")
                .setHeader(ISSUE_PROJECT_KEY, () -> "COM")
                .setHeader(ISSUE_TYPE_NAME, () -> "Bug")
                .setHeader(ISSUE_SUMMARY, () -> "Example Demo Bug jira " + (new Date()))
                .setHeader(ISSUE_PRIORITY_NAME, () -> "Low")

                // uncomment to add a component
                // .setHeader(ISSUE_COMPONENTS, () -> {
                //     List<String> comps = new ArrayList<>();
                //     comps.add("plugins");
                //     return comps;
                // })
                .setBody(() -> "A small description for a test issue. ")
                .log("  JIRA new issue: ${body}")
                .to("jira://addIssue");
    }

}
