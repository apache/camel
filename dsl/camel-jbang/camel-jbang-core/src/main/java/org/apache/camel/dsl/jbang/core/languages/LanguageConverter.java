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
package org.apache.camel.dsl.jbang.core.languages;

import java.util.regex.Matcher;

import org.apache.camel.dsl.jbang.core.api.Converter;
import org.apache.camel.dsl.jbang.core.types.Language;

public class LanguageConverter implements Converter<Language> {

    @Override
    public Language convert(Matcher matcher) {
        Language language = new Language();

        language.name = matcher.group(2).replace(".adoc", "");
        language.shortName = language.name.replace("-language", "");
        language.description = matcher.group(3);
        language.link = String.format("https://camel.apache.org/components/latest/languages/%s.html", language.name);

        return language;
    }
}
