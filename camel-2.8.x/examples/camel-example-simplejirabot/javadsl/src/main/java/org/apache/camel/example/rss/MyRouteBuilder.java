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
package org.apache.camel.example.rss;

import org.apache.camel.builder.RouteBuilder;


/**
 * A simple example router demonstrating the camel-rss component.
 */
public class MyRouteBuilder extends RouteBuilder {

    public void configure() {

        String rssURL = "https://issues.apache.org/jira/sr/jira.issueviews:searchrequest-rss/temp/SearchRequest.xml"
                + "?pid=12311211&sorter/field=issuekey&sorter/order=DESC&tempMax=1000&delay=10s";

        // START SNIPPET: e1
        from("rss:" + rssURL).
                marshal().rss().
                setBody(xpath("/rss/channel/item/title/text()")).
                transform(body().prepend("Jira: ")).
                to("log:jirabot?showHeaders=false&showExchangePattern=false&showBodyType=false").
                to("irc:JiraBot@irc.freenode.net/#jirabottest");
        // END SNIPPET: e1

    }
}
