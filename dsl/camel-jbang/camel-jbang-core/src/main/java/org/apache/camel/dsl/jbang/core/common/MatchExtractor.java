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
package org.apache.camel.dsl.jbang.core.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.jbang.core.api.Converter;
import org.apache.camel.dsl.jbang.core.api.Extractor;
import org.apache.camel.dsl.jbang.core.api.Printer;

public class MatchExtractor<T> implements Extractor {
    private final Pattern pattern;
    private final Converter<T> converter;
    private final Printer<T> injector;

    public MatchExtractor(Pattern pattern, Converter<T> converter, Printer<T> injector) {
        this.pattern = pattern;
        this.converter = converter;
        this.injector = injector;
    }

    @Override
    public void extract(String line) {
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            T data = converter.convert(matcher);
            injector.inject(data);
        }
    }
}
