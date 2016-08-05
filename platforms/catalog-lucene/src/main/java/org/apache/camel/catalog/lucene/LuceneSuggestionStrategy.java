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
package org.apache.camel.catalog.lucene;

import java.io.StringReader;
import java.util.Set;

import org.apache.camel.catalog.SuggestionStrategy;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.RAMDirectory;

/**
 * Apache Lucene based {@link SuggestionStrategy}.
 * <p/>
 * This is a simple implementation using in-memory directory and no state.
 */
public class LuceneSuggestionStrategy implements SuggestionStrategy {

    private int maxSuggestions = 3;

    @Override
    public String[] suggestEndpointOptions(Set<String> names, String unknownOption) {
        // each option must be on a separate line in a String
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            sb.append(name);
            sb.append("\n");
        }
        StringReader reader = new StringReader(sb.toString());

        try {
            PlainTextDictionary words = new PlainTextDictionary(reader);

            // use in-memory lucene spell checker to make the suggestions
            RAMDirectory dir = new RAMDirectory();
            SpellChecker checker = new SpellChecker(dir);
            checker.indexDictionary(words, new IndexWriterConfig(new KeywordAnalyzer()), false);

            return checker.suggestSimilar(unknownOption, maxSuggestions);
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

}
