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

import java.util.List;

import org.apache.camel.model.ChoiceType;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.WhenType;

/**
 * Helper class for ProcessorType and the other model classes.
 */
public final class ProcessorTypeHelper {

    private ProcessorTypeHelper() {
    }

    /**
     * Looks for the given type in the list of outputs and recurring all the children as well.
     * Will stop at first found and return it.
     *
     * @param outputs  list of outputs, can be null or empty.
     * @param type     the type to look for
     * @return         the first found type, or <tt>null</tt> if not found
     */
    @SuppressWarnings("unchecked")
    public static <T> T findFirstTypeInOutputs(List<ProcessorType> outputs, Class<T> type) {
        if (outputs == null || outputs.isEmpty()) {
            return null;
        }

        for (ProcessorType out : outputs) {
            if (type.isInstance(out)) {
                return type.cast(out);
            }

            // special for choice
            if (out instanceof ChoiceType) {
                ChoiceType choice = (ChoiceType) out;
                for (WhenType when : choice.getWhenClauses()) {
                    List<ProcessorType> children = when.getOutputs();
                    T child = findFirstTypeInOutputs(children, type);
                    if (child != null) {
                        return child;
                    }
                }

                List<ProcessorType> children = choice.getOtherwise().getOutputs();
                T child = findFirstTypeInOutputs(children, type);
                if (child != null) {
                    return child;
                }
            }

            // try children as well
            List<ProcessorType> children = out.getOutputs();
            T child = findFirstTypeInOutputs(children, type);
            if (child != null) {
                return child;
            }
        }

        return null;
    }

}
