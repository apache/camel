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
package org.apache.camel.impl.verifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.camel.ComponentVerifier;
import org.apache.camel.util.ObjectHelper;

public final class ResultErrorHelper {

    private ResultErrorHelper() {
    }

    // **********************************
    // Helpers
    // **********************************

    /**
     *
     * @param parameterName the required option
     * @param parameters the
     * @return
     */
    public static Optional<ComponentVerifier.Error> requiresOption(String parameterName, Map<String, Object> parameters) {
        if (ObjectHelper.isEmpty(parameters.get(parameterName))) {
            return Optional.of(
                ResultErrorBuilder.withMissingOption(parameterName).build()
            );
        }

        return Optional.empty();
    }

    public static List<ComponentVerifier.Error> requiresAny(Map<String, Object> parameters, OptionsGroup... groups) {
        return requiresAny(parameters, Arrays.asList(groups));
    }

    public static List<ComponentVerifier.Error> requiresAny(Map<String, Object> parameters, Collection<OptionsGroup> groups) {
        final List<ComponentVerifier.Error> errors = new ArrayList<>();
        final Set<String> keys = new HashSet<>(parameters.keySet());

        for (OptionsGroup group : groups) {
            if (keys.containsAll(group.getOptions())) {
                // All the options of this group are found so we are good
                return Collections.emptyList();
            } else {
                ResultErrorBuilder builder = new ResultErrorBuilder()
                    .code(ComponentVerifier.CODE_INCOMPLETE_OPTION_GROUP)
                    .attribute(ComponentVerifier.GROUP_NAME, group.getName())
                    .attribute(ComponentVerifier.GROUP_OPTIONS, String.join(",", group.getOptions()));

                for (String option : group.getOptions()) {
                    if (!parameters.containsKey(option)) {
                        builder.parameter(option);
                    }
                }

                errors.add(builder.build());
            }
        }

        return errors;
    }
}
