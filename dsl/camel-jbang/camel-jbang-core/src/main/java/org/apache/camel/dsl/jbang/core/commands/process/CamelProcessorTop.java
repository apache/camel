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

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import picocli.CommandLine.Command;

@Command(name = "processor", description = "Top performing processors",
         sortOptions = false)
public class CamelProcessorTop extends CamelProcessorStatus {

    public CamelProcessorTop(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected int sortRow(Row o1, Row o2) {
        // sort for highest mean value as we want the slowest in the top
        long m1 = o1.mean != null ? Long.parseLong(o1.mean) : 0;
        long m2 = o2.mean != null ? Long.parseLong(o2.mean) : 0;
        if (m1 < m2) {
            return 1;
        } else if (m1 > m2) {
            return -1;
        } else {
            return 0;
        }
    }

}
