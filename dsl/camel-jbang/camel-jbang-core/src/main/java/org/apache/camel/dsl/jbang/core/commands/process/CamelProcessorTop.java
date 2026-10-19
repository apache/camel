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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.util.Comparator;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ListingFormat;
import picocli.CommandLine.Command;

@Command(name = "processor", description = "Top performing processors",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel top processor",
                 "  camel top processor --watch" })
public class CamelProcessorTop extends CamelProcessorStatus {

    public CamelProcessorTop(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected Comparator<Row> order() {
        // then the slowest first, as the top shows the slowest
        return super.order().thenComparing(ListingFormat.<Row> slowestFirst(sort, r -> r.mean));
    }

}
