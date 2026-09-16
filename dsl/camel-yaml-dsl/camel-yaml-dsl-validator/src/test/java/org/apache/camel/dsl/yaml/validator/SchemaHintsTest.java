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
package org.apache.camel.dsl.yaml.validator;

import java.text.MessageFormat;
import java.util.List;

import com.networknt.schema.Error;
import com.networknt.schema.path.NodePath;
import com.networknt.schema.path.PathType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24715: the matcher behind the hint tables. The texts of the rows are pinned by the YamlValidator*Test classes
 * through the validator; this pins how a row is selected and how the error is rewritten.
 */
public class SchemaHintsTest {

    private static final YamlValidator VALIDATOR = new YamlValidator();

    private static Error error(String keyword, String location, String message) {
        NodePath path = new NodePath(PathType.JSON_POINTER);
        for (String segment : location.split("/")) {
            if (!segment.isEmpty()) {
                path = segment.matches("\\d+") ? path.append(Integer.parseInt(segment)) : path.append(segment);
            }
        }
        return Error.builder().keyword(keyword).instanceLocation(path).messageKey(keyword)
                .format(new MessageFormat("{0}")).arguments(message).build();
    }

    @Test
    public void testFirstMatchingRowWins() {
        List<SchemaHints.Hint> table = List.of(
                SchemaHints.append("type", ".*/beans", m -> true, m -> "first"),
                SchemaHints.append("type", ".*/beans", m -> true, m -> "second"),
                SchemaHints.append("type", null, m -> true, m -> "any"));
        Error beans = SchemaHints.apply(table, error("type", "/0/beans", "object found, array expected"), VALIDATOR);
        assertThat(beans.getMessage()).isEqualTo("object found, array expected (first)");
        Error other = SchemaHints.apply(table, error("type", "/0/route/from", "object found, array expected"), VALIDATOR);
        assertThat(other.getMessage()).isEqualTo("object found, array expected (any)");
    }

    @Test
    public void testKeywordLocationAndConditionSelectTheRow() {
        List<SchemaHints.Hint> table = List.of(
                SchemaHints.append("type", "/\\d+/steps/\\d+", m -> m.message().contains("array expected"), m -> "hint"));
        Error unrelatedKeyword = error("required", "/0/steps/1", "array expected");
        Error unrelatedLocation = error("type", "/0/steps", "array expected");
        Error unrelatedMessage = error("type", "/0/steps/1", "object expected");
        Error match = error("type", "/0/steps/1", "array expected");
        assertThat(SchemaHints.apply(table, unrelatedKeyword, VALIDATOR)).isSameAs(unrelatedKeyword);
        assertThat(SchemaHints.apply(table, unrelatedLocation, VALIDATOR)).isSameAs(unrelatedLocation);
        assertThat(SchemaHints.apply(table, unrelatedMessage, VALIDATOR)).isSameAs(unrelatedMessage);
        assertThat(SchemaHints.apply(table, match, VALIDATOR).getMessage()).isEqualTo("array expected (hint)");
    }

    @Test
    public void testReplaceRewritesKeywordAndMessage() {
        List<SchemaHints.Hint> table = List.of(
                SchemaHints.replace("additionalProperties", null, m -> "simple".equals(m.unknown()),
                        m -> m.name() + ": {" + m.unknown() + ": ...} is the compact notation", "compactNotation",
                        "compactNotation"));
        Error hinted = SchemaHints.apply(table,
                error("additionalProperties", "/0/route/from/steps/0/setBody", "property 'simple' is not defined"),
                VALIDATOR);
        assertThat(hinted.getKeyword()).isEqualTo("compactNotation");
        assertThat(hinted.getMessageKey()).isEqualTo("compactNotation");
        assertThat(hinted.getMessage()).isEqualTo("setBody: {simple: ...} is the compact notation");
        assertThat(String.valueOf(hinted.getInstanceLocation())).isEqualTo("/0/route/from/steps/0/setBody");
    }

    @Test
    public void testUnknownPropertyRowNeedsTheName() {
        List<SchemaHints.Hint> table = List.of(
                SchemaHints.unknownProperty(null, m -> true, m -> "did you mean '" + m.unknown() + "'?"));
        Error noName = error("additionalProperties", "/0/log", "not defined in the schema");
        assertThat(SchemaHints.apply(table, noName, VALIDATOR)).isSameAs(noName);
        Error named = error("additionalProperties", "/0/log", "property 'lvl' is not defined in the schema");
        assertThat(SchemaHints.apply(table, named, VALIDATOR).getMessage())
                .isEqualTo("property 'lvl' is not defined in the schema (did you mean 'lvl'?)");
    }

    @Test
    public void testSameHintAtSameLocationReportedOnce() {
        List<SchemaHints.Hint> table = List.of(
                SchemaHints.replace("type", null, m -> true, m -> "an expression expected", "type", "expression"));
        List<Error> errors = List.of(
                error("type", "/0/onException/handled", "boolean found, object expected"),
                error("type", "/0/onException/handled", "boolean found, string expected"),
                error("type", "/0/onException/continued", "boolean found, object expected"),
                error("required", "/0/onException", "required property 'steps' not found"));
        List<Error> hinted = SchemaHints.apply(table, errors, VALIDATOR);
        assertThat(hinted).hasSize(3);
        assertThat(hinted.get(0).getMessage()).isEqualTo("an expression expected");
        assertThat(String.valueOf(hinted.get(1).getInstanceLocation())).isEqualTo("/0/onException/continued");
        assertThat(hinted.get(2)).isSameAs(errors.get(3));
    }

    @Test
    public void testMatchNamesTheItemAndItsParent() {
        SchemaHints.Match m = SchemaHints.Match.of(
                error("additionalProperties", "/0/route/from/steps/2/choice/when/0", "property 'log' is not defined"),
                VALIDATOR);
        assertThat(m.name()).isEqualTo("0");
        assertThat(m.nameIsIndex()).isTrue();
        assertThat(m.parentName()).isEqualTo("when");
        assertThat(m.unknown()).isEqualTo("log");
        assertThat(m.locationEndsWith("/when/0")).isTrue();
    }
}
