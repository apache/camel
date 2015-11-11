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
package org.apache.camel.tools.apt.helper;

import java.util.Comparator;

import org.apache.camel.tools.apt.model.EndpointOption;

public final class EndpointHelper {

    private EndpointHelper() {
    }

    /**
     * Returns the group name from the given label.
     * <p/>
     * The group name is a single name deducted from the label. The label can contain multiple names separated by comma.
     * The group is the best guess as a group of those labels, so similar labels can be combined into the same group.
     *
     * @param label          the label
     * @param consumerOnly   whether the component is consumer only
     * @param producerOnly   whether the component is producer only
     * @return the group name
     */
    public static String labelAsGroupName(String label, boolean consumerOnly, boolean producerOnly) {
        // if there is no label then use common as fallback
        String answer = "common";
        if (consumerOnly) {
            answer = "consumer";
        } else if (producerOnly) {
            answer = "producer";
        }

        String value = label;
        if (!Strings.isNullOrEmpty(value)) {

            // we want to put advanced into own group, so look for a label that has advanced as prefix x,advanced => x (advanced)
            value = value.replaceFirst("(\\w),(advanced)", "$1 (advanced)");

            String[] array = value.split(",");
            // grab last label which is the most specific label we want to use for the tab
            answer = array[array.length - 1];
            // if we are in consumer/producer only mode, then enrich the advanced label to indicate its advanced of those
            if (answer.equals("advanced") && consumerOnly) {
                answer = "consumer (advanced)";
            } else if (answer.equals("advanced") && producerOnly) {
                answer = "producer (advanced)";
            }
        }

        return answer;
    }

    /**
     * A comparator to sort the endpoint/component options according to group and label.
     */
    public static EndpointOptionGroupAndLabelComparator createGroupAndLabelComparator() {
        return new EndpointOptionGroupAndLabelComparator();
    }

    private static final class EndpointOptionGroupAndLabelComparator implements Comparator<EndpointOption> {

        @Override
        public int compare(EndpointOption o1, EndpointOption o2) {
            String name1 = o1.getName();
            String name2 = o2.getName();
            String label1 = o1.getLabel() != null ? o1.getLabel() : "common";
            String label2 = o2.getLabel() != null ? o2.getLabel() : "common";
            String group1 = o1.getGroup();
            String group2 = o2.getGroup();

            // if same label or group then sort by name
            if (label1.equalsIgnoreCase(label2) || group1.equalsIgnoreCase(group2)) {
                return name1.compareToIgnoreCase(name2);
            }

            int score1 = groupScore(group1);
            int score2 = groupScore(group2);

            if (score1 < score2) {
                return -1;
            } else if (score2 < score1) {
                return 1;
            } else {
                // compare by full label and name
                int score = label1.compareToIgnoreCase(label2);
                if (score == 0) {
                    score = name1.compareToIgnoreCase(name2);
                }
                return score;
            }
        }
    }

    private static int groupScore(String group) {
        if ("common".equals(group)) {
            return 1;
        } else if ("common (advanced)".equals(group)) {
            return 2;
        } else if ("consumer".equals(group)) {
            return 3;
        } else if ("consumer (advanced)".equals(group)) {
            return 4;
        } else if ("producer".equals(group)) {
            return 5;
        } else if ("producer (advanced)".equals(group)) {
            return 6;
        } else {
            return 9;
        }
    }

}
