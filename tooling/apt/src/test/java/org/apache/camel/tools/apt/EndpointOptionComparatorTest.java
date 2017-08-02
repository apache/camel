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
package org.apache.camel.tools.apt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.tools.apt.helper.EndpointHelper;
import org.apache.camel.tools.apt.model.EndpointOption;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EndpointOptionComparatorTest {

    @Test
    public void testComparator() {
        String label1 = "common";
        String label2 = "advanced";
        String label3 = "common";
        String label4 = "filter";
        String group1 = EndpointHelper.labelAsGroupName(label1, false, false);
        String group2 = EndpointHelper.labelAsGroupName(label2, false, false);
        String group3 = EndpointHelper.labelAsGroupName(label3, false, false);
        String group4 = EndpointHelper.labelAsGroupName(label4, false, false);

        EndpointOption op1 = new EndpointOption("first", "First", "string", "true", "", "", "blah", null, null, false,
            false, null, false, group1, label1, false, null);
        EndpointOption op2 = new EndpointOption("synchronous", "Synchronous", "string", "true", "", "", "blah", null, null, false,
            false, null, false, group2, label2, false, null);
        EndpointOption op3 = new EndpointOption("second", "Second", "string", "true", "", "", "blah", null, null, false,
            false, null, false, group3, label3, false, null);
        EndpointOption op4 = new EndpointOption("country", "Country", "string", "true", "", "", "blah", null, null, false,
            false, null, false, group4, label4, false, null);

        List<EndpointOption> list = new ArrayList<EndpointOption>();
        list.add(op1);
        list.add(op2);
        list.add(op3);
        list.add(op4);

        // then by label into the groups
        Collections.sort(list, EndpointHelper.createGroupAndLabelComparator());

        assertEquals("first", list.get(0).getName()); // common
        assertEquals("second", list.get(1).getName()); // common
        assertEquals("synchronous", list.get(2).getName()); // advanced
        assertEquals("country", list.get(3).getName()); // filter
    }
}
