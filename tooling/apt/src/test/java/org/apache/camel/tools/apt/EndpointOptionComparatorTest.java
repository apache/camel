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
package org.apache.camel.tools.apt;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tools.apt.helper.EndpointHelper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

        EndpointOptionModel op1 = new EndpointOptionModel("first", "First", "string", true, "", "", "blah", null, false,
            false, null, false, group1, label1, null, null, null);
        EndpointOptionModel op2 = new EndpointOptionModel("synchronous", "Synchronous", "string", true, "", "blah", null, null, false,
            false, null, false, group2, label2, null, null, null);
        EndpointOptionModel op3 = new EndpointOptionModel("second", "Second", "string", true, "", "blah", null, null, false,
            false, null, false, group3, label3, null, null, null);
        EndpointOptionModel op4 = new EndpointOptionModel("country", "Country", "string", true, "", "blah", null, null, false,
            false, null, false, group4, label4, null, null, null);

        List<EndpointOptionModel> list = new ArrayList<>();
        list.add(op1);
        list.add(op2);
        list.add(op3);
        list.add(op4);

        // then by label into the groups
        list.sort(EndpointHelper.createGroupAndLabelComparator());

        Assertions.assertEquals("first", list.get(0).getName()); // common
        Assertions.assertEquals("second", list.get(1).getName()); // common
        Assertions.assertEquals("synchronous", list.get(2).getName()); // advanced
        Assertions.assertEquals("country", list.get(3).getName()); // filter
    }

    public static class EndpointOptionModel extends BaseOptionModel {
        public EndpointOptionModel(String name, String displayName, String type, boolean required, String defaultValue,
                                   String description, String optionalPrefix, String prefix,
                                   boolean multiValue, boolean deprecated, String deprecationNote, boolean secret,
                                   String group, String label, List<String> enums,
                                   String configurationClass, String nestedFieldName) {
            this.name = name;
            this.displayName = displayName;
            this.type = type;
            this.required = required;
            this.defaultValue = defaultValue;
            this.description = description;
            this.optionalPrefix = optionalPrefix;
            this.prefix = prefix;
            this.multiValue = multiValue;
            this.deprecated = deprecated;
            this.deprecationNote = deprecationNote;
            this.secret = secret;
            this.group = group;
            this.label = label;
            this.enums = enums;
            this.configurationClass = configurationClass;
            this.configurationField = nestedFieldName;
        }

    }
}
