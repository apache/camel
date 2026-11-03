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
package org.apache.camel.jsonpath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jayway.jsonpath.Option;
import org.apache.camel.Converter;

/**
 * Converts the JSONPath options from text, such as camel.language.jsonpath.options =
 * SUPPRESS_EXCEPTIONS,ALWAYS_RETURN_LIST.
 */
@Converter(generateLoader = true)
public final class JsonPathConverter {

    private JsonPathConverter() {
    }

    /**
     * The options of a comma separated list of their names, which may have spaces around them, be in any case and use
     * dashes or camel case (SUPPRESS_EXCEPTIONS, suppress-exceptions and suppressExceptions are the same option).
     */
    @Converter
    public static Option[] toOptions(String value) {
        List<Option> answer = new ArrayList<>();
        for (String name : value.split(",")) {
            name = name.trim();
            if (!name.isEmpty()) {
                answer.add(toOption(name));
            }
        }
        return answer.toArray(new Option[0]);
    }

    private static Option toOption(String name) {
        String key = name.replace("-", "").replace("_", "");
        for (Option option : Option.values()) {
            if (option.name().replace("_", "").equalsIgnoreCase(key)) {
                return option;
            }
        }
        throw new IllegalArgumentException(
                "Unknown JSONPath option: " + name + ". The options are: " + Arrays.toString(Option.values()));
    }
}
