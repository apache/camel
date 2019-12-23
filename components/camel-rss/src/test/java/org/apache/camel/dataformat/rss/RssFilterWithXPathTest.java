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
package org.apache.camel.dataformat.rss;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rss.RssFilterTest;

public class RssFilterWithXPathTest extends RssFilterTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: ex
                // only entries with Camel in the title will get through the filter
                from("rss:file:src/test/data/rss20.xml?splitEntries=true&delay=100")
                    .marshal().rss().filter().xpath("//item/title[contains(.,'Camel')]").to("mock:result");
                // END SNIPPET: ex
            }
        };
    }

}
