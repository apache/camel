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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.Option;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JSONPath options as text: camel.language.jsonpath.options on the language, and the option of an expression.
 */
public class JsonPathOptionsTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testConvertTheOptions() {
        Option[] options = context.getTypeConverter().convertTo(Option[].class,
                " suppress_exceptions , ALWAYS_RETURN_LIST,always-return-list,, asPathList ");
        assertArrayEquals(new Option[] {
                Option.SUPPRESS_EXCEPTIONS, Option.ALWAYS_RETURN_LIST, Option.ALWAYS_RETURN_LIST, Option.AS_PATH_LIST },
                options);

        IllegalArgumentException e
                = assertThrows(IllegalArgumentException.class, () -> JsonPathConverter.toOptions("SUPPRESS_EXCEPTIONS,FOO"));
        assertTrue(e.getMessage().startsWith("Unknown JSONPath option: FOO"), e.getMessage());
    }

    @Test
    public void testSetTheOptionsOfTheLanguage() {
        JsonPathLanguage language = (JsonPathLanguage) context.resolveLanguage("jsonpath");
        // as camel.language.jsonpath.options = SUPPRESS_EXCEPTIONS,ALWAYS_RETURN_LIST does
        PropertyBindingSupport.build().bind(context, language, "options", "SUPPRESS_EXCEPTIONS,ALWAYS_RETURN_LIST");
        assertArrayEquals(new Option[] { Option.SUPPRESS_EXCEPTIONS, Option.ALWAYS_RETURN_LIST }, language.getOptions());

        // the options apply to the expressions of the language: a missing path is an empty list, not an error
        Expression expression = language.createExpression("$.missing");
        assertEquals(List.of(), expression.evaluate(exchange(), List.class));
    }

    @Test
    public void testTheConfigurerOfTheLanguage() {
        // camel-main finds the configurer of camel.language.jsonpath.* by the name of the language
        PropertyConfigurer configurer
                = PluginHelper.getConfigurerResolver(context).resolvePropertyConfigurer("jsonpath-language", context);
        assertInstanceOf(JsonPathLanguageConfigurer.class, configurer);

        JsonPathLanguage language = (JsonPathLanguage) context.resolveLanguage("jsonpath");
        PropertyBindingSupport.build().withConfigurer(configurer).withReflection(false)
                .bind(context, language, new HashMap<>(Map.of("options", "SUPPRESS_EXCEPTIONS", "writeAsString", "true")));
        assertArrayEquals(new Option[] { Option.SUPPRESS_EXCEPTIONS }, language.getOptions());
        assertTrue(language.isWriteAsString());
    }

    @Test
    public void testTheOptionOfAnExpression() {
        JsonPathLanguage language = (JsonPathLanguage) context.resolveLanguage("jsonpath");
        Object[] properties = new Object[8];
        properties[7] = " suppress_exceptions , ALWAYS_RETURN_LIST ";
        Expression expression = language.createExpression(null, "$.missing", properties);
        expression.init(context);
        assertEquals(List.of(), expression.evaluate(exchange(), List.class));
    }

    private Exchange exchange() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("{\"a\": 1}");
        return exchange;
    }
}
