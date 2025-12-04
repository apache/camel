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

package org.apache.camel.catalog.suggest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.camel.catalog.SuggestionStrategy;
import org.apache.commons.codec.language.Soundex;

/**
 * Phonetic soundex based {@link SuggestionStrategy}.
 */
public class CatalogSuggestionStrategy implements SuggestionStrategy {

    private static final int MAX_SUGGESTIONS = 5;

    @Override
    public String[] suggestEndpointOptions(Set<String> names, String unknownOption) {
        return suggestEndpointOptions(names, unknownOption, MAX_SUGGESTIONS);
    }

    public static String[] suggestEndpointOptions(Collection<String> names, String unknownOption, int maxSuggestions) {
        List<String> answer = new ArrayList<>();
        Soundex soundex = Soundex.US_ENGLISH_SIMPLIFIED;

        List<String> sort = new ArrayList<>(names);
        Collections.sort(sort);

        try {
            // try highest match first
            for (String name : sort) {
                int value = soundex.difference(unknownOption, name);
                if (value == 4 && answer.size() < maxSuggestions) {
                    answer.add(name);
                }
            }
            // then the 2nd-best
            for (String name : sort) {
                int value = soundex.difference(unknownOption, name);
                if (value == 3 && answer.size() < maxSuggestions) {
                    if (!answer.contains(name)) {
                        answer.add(name);
                    }
                }
            }
            // then options starting with the same
            if (answer.size() < maxSuggestions && unknownOption.length() >= 4) {
                unknownOption = unknownOption.toLowerCase(Locale.ROOT);
                for (String name : sort) {
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (answer.size() < maxSuggestions && lower.startsWith(unknownOption)) {
                        if (!answer.contains(name)) {
                            answer.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return answer.toArray(new String[0]);
    }
}
