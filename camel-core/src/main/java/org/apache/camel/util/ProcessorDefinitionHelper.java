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
package org.apache.camel.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.WhenDefinition;

/**
 * Helper class for ProcessorType and the other model classes.
 */
public final class ProcessorDefinitionHelper {

    private ProcessorDefinitionHelper() {
    }

    /**
     * Looks for the given type in the list of outputs and recurring all the children as well.
     *
     * @param outputs  list of outputs, can be null or empty.
     * @param type     the type to look for
     * @return         the found definitions, or <tt>null</tt> if not found
     */
    public static <T> Iterator<T> filterTypeInOutputs(List<ProcessorDefinition> outputs, Class<T> type) {
        List<T> found = new ArrayList<T>();
        doFindType(outputs, type, found);
        return found.iterator();
    }

    /**
     * Looks for the given type in the list of outputs and recurring all the children as well.
     * Will stop at first found and return it.
     *
     * @param outputs  list of outputs, can be null or empty.
     * @param type     the type to look for
     * @return         the first found type, or <tt>null</tt> if not found
     */
    public static <T> T findFirstTypeInOutputs(List<ProcessorDefinition> outputs, Class<T> type) {
        List<T> found = new ArrayList<T>();
        doFindType(outputs, type, found);
        if (found.isEmpty()) {
            return null;
        }
        return found.iterator().next();
    }

    @SuppressWarnings("unchecked")
    private static void doFindType(List<ProcessorDefinition> outputs, Class<?> type, List found) {
        if (outputs == null || outputs.isEmpty()) {
            return;
        }

        for (ProcessorDefinition out : outputs) {
            if (type.isInstance(out)) {
                found.add(out);
            }

            // send is much common
            if (out instanceof SendDefinition) {
                SendDefinition send = (SendDefinition) out;
                List<ProcessorDefinition> children = send.getOutputs();
                doFindType(children, type, found);
            }

            // special for choice
            if (out instanceof ChoiceDefinition) {
                ChoiceDefinition choice = (ChoiceDefinition) out;
                for (WhenDefinition when : choice.getWhenClauses()) {
                    List<ProcessorDefinition> children = when.getOutputs();
                    doFindType(children, type, found);
                }

                // otherwise is optional
                if (choice.getOtherwise() != null) {
                    List<ProcessorDefinition> children = choice.getOtherwise().getOutputs();
                    doFindType(children, type, found);
                }
            }

            // try children as well
            List<ProcessorDefinition> children = out.getOutputs();
            doFindType(children, type, found);
        }
    }

}
