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

import java.util.Collection;
import java.util.Set;

import org.apache.camel.catalog.SuggestionStrategy;
import org.apache.camel.catalog.impl.EditDistanceSuggestionStrategy;

/**
 * Edit distance based {@link SuggestionStrategy}.
 *
 * @deprecated since 4.23 the catalog suggests option names by default with {@link EditDistanceSuggestionStrategy} from
 *             camel-core-catalog; this module is no longer needed.
 */
@Deprecated(since = "4.23.0")
public class CatalogSuggestionStrategy implements SuggestionStrategy {

    private static final int MAX_SUGGESTIONS = 5;

    @Override
    public String[] suggestEndpointOptions(Set<String> names, String unknownOption) {
        return suggestEndpointOptions(names, unknownOption, MAX_SUGGESTIONS);
    }

    public static String[] suggestEndpointOptions(Collection<String> names, String unknownOption, int maxSuggestions) {
        return EditDistanceSuggestionStrategy.suggest(names, unknownOption, maxSuggestions);
    }
}
