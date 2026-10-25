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
package org.apache.camel.tooling.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.tooling.model.BodyTypeModel.OptionBodyTypeModel;
import org.apache.camel.tooling.model.BodyTypeResolver.Resolved;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule the tools share to read a side of the body types for the options a step sets (CAMEL-25928).
 */
class BodyTypeResolverTest {

    @Test
    void theCarriersWithTheDefaultOptions() {
        BodyTypeModel side = side(List.of("map", "list"));
        side.setFormat("json");
        assertEquals(new Resolved(List.of("map", "list"), null, "json"), resolve(side, Map.of()));
        assertNull(resolve(side(List.of("unknown")), Map.of()));
        assertNull(resolve(null, Map.of()));
    }

    @Test
    void aSideFromAnotherModelIsResolvedByTheCaller() {
        BodyTypeModel side = new BodyTypeModel();
        side.setFrom(BodyTypeModel.MARSHAL_OUT);
        assertNull(resolve(side, Map.of()));
    }

    @Test
    void aValueKnownOnlyWhenTheRouteRunsIsNotKnown() {
        BodyTypeModel side = jackson();
        assertNull(resolve(side, Map.of("unmarshalType", "{{type}}")));
        assertNull(resolve(side, Map.of("useList", "${header.list}")));
        // an option the component can set, and the step does not
        assertNull(BodyTypeResolver.resolve(side, Map.of(), Set.of("useList")));
    }

    @Test
    void anOptionTheBodyDependsOnDecidesFirst() {
        BodyTypeModel side = jackson();
        assertEquals(List.of("list"), resolve(side, Map.of("useList", "true", "unmarshalType", "com.acme.Order")).carriers());
        // no body for its value: not known, also with the class
        assertNull(resolve(side, Map.of("collectionType", "java.util.Set", "unmarshalType", "com.acme.Order")));
        assertNull(resolve(side, Map.of("useList", "false")));
    }

    @Test
    void theOptionThatNamesTheClassOfTheBody() {
        BodyTypeModel side = jackson();
        Resolved order = resolve(side, Map.of("unmarshalType", "com.acme.Order"));
        assertEquals(new Resolved(List.of("pojo", "null"), "com.acme.Order", null), order);
        assertEquals(List.of("text", "null"), resolve(side, Map.of("unmarshalType", "String")).carriers());
        // Object can be anything
        assertNull(resolve(side, Map.of("unmarshalType", "java.lang.Object")));
    }

    @Test
    void theOptionThatNamesTheClassWinsOverTheValuesOfOtherOptions() {
        BodyTypeModel side = side(List.of("pojo"));
        side.setJavaType("org.w3c.dom.NodeList");
        side.setJavaTypeOption("resultType");
        side.getByOption().add(when("resultQName", "STRING", List.of("text"), null));
        assertEquals(List.of("text"), resolve(side, Map.of("resultQName", "string")).carriers());
        assertEquals(List.of("value", "null"),
                resolve(side, Map.of("resultQName", "STRING", "resultType", "java.lang.Integer")).carriers());
    }

    @Test
    void anOptionValueCanNameTheClassToo() {
        BodyTypeModel side = side(List.of("list", "unchanged"));
        side.getByOption().add(when("outputType", "SelectOne", List.of("map", "value", "null"), "outputClass"));
        side.getByOption().add(when("outputType", "StreamList", List.of("iterator"), null));
        assertEquals(List.of("map", "value", "null"), resolve(side, Map.of("outputType", "SelectOne")).carriers());
        assertEquals(new Resolved(List.of("pojo", "null"), "com.acme.Order", null),
                resolve(side, Map.of("outputType", "SelectOne", "outputClass", "com.acme.Order")));
        // Object can be anything, also for an option value
        assertNull(resolve(side, Map.of("outputType", "SelectOne", "outputClass", "java.lang.Object")));
        // the class does not change the List of the default outputType
        assertEquals(List.of("list", "unchanged"), resolve(side, Map.of("outputClass", "com.acme.Order")).carriers());
        assertEquals(List.of("iterator"), resolve(side, Map.of("outputType", "stream-list")).carriers());
    }

    @Test
    void theCarriersAreACopy() {
        BodyTypeModel side = side(new ArrayList<>(List.of("text")));
        Resolved resolved = resolve(side, Map.of());
        side.getCarriers().add("bytes");
        assertEquals(List.of("text"), resolved.carriers());
    }

    @Test
    void twoOptionValuesThatSayDifferentBodiesAreNotKnown() {
        BodyTypeModel side = side(List.of("text"));
        side.getByOption().add(when("output", "bytes", List.of("bytes"), null));
        side.getByOption().add(when("binary", "true", List.of("stream"), null));
        assertNull(resolve(side, Map.of("output", "bytes", "binary", "true")));
        assertEquals(List.of("bytes"), resolve(side, Map.of("output", "BYTES")).carriers());
    }

    @Test
    void anyValueOfAnOptionWinsOverTheRest() {
        // the body after an enrich: the reply of the endpoint, unless variableReceive keeps the body
        BodyTypeModel side = new BodyTypeModel();
        side.setFrom(BodyTypeModel.PRODUCER_OUT);
        side.setDependsOn(List.of("aggregationStrategy"));
        side.getByOption().add(when("variableReceive", BodyTypeModel.ANY_VALUE, List.of("unchanged"), null));
        assertEquals(List.of("unchanged"), resolve(side, Map.of("variableReceive", "reply")).carriers());
        assertEquals(List.of("unchanged"), resolve(side, Map.of("variableReceive", "{{name}}")).carriers());
        assertEquals(List.of("unchanged"),
                resolve(side, Map.of("variableReceive", "reply", "aggregationStrategy", "#myStrategy")).carriers());
        // not set: the caller follows from
        assertNull(resolve(side, Map.of()));
        assertNull(resolve(side, Map.of("aggregationStrategy", "#myStrategy")));
        // it may be set elsewhere
        assertNull(BodyTypeResolver.resolve(side, Map.of(), Set.of("variableReceive")));
    }

    @Test
    void anyValueIsNotAValueOfAnotherOption() {
        BodyTypeModel side = side(List.of("text"));
        side.getByOption().add(when("variableReceive", BodyTypeModel.ANY_VALUE, List.of("unchanged"), null));
        assertEquals(List.of("text"), resolve(side, Map.of("other", "*")).carriers());
        side.getByOption().add(when("variableSend", BodyTypeModel.ANY_VALUE, List.of("null"), null));
        // two options set that say different bodies
        assertNull(resolve(side, Map.of("variableReceive", "a", "variableSend", "b")));
    }

    @Test
    void theValuesOfAnEnumInAnyStyle() {
        assertTrue(BodyTypeResolver.isSameValue("convert-to-json", "CONVERT_TO_JSON"));
        assertTrue(BodyTypeResolver.isSameValue("convertToJson", "CONVERT_TO_JSON"));
        assertTrue(BodyTypeResolver.isSameValue("selectOne", "SelectOne"));
        assertFalse(BodyTypeResolver.isSameValue("SelectList", "SelectOne"));
    }

    private static Resolved resolve(BodyTypeModel side, Map<String, String> options) {
        return BodyTypeResolver.resolve(side, options, Set.of());
    }

    /** The body after unmarshal of jackson: a Map or a List, a List with useList, or the class of unmarshalType. */
    private static BodyTypeModel jackson() {
        BodyTypeModel side = side(List.of("map", "list"));
        side.setDependsOn(List.of("collectionType", "useList", "allowUnmarshallType"));
        side.setJavaTypeOption("unmarshalType");
        side.getByOption().add(when("useList", "true", List.of("list"), null));
        return side;
    }

    private static BodyTypeModel side(List<String> carriers) {
        BodyTypeModel side = new BodyTypeModel();
        side.setCarriers(carriers);
        return side;
    }

    private static OptionBodyTypeModel when(String option, String value, List<String> carriers, String javaTypeOption) {
        OptionBodyTypeModel when = new OptionBodyTypeModel();
        when.setOption(option);
        when.setValue(value);
        when.setCarriers(carriers);
        when.setJavaTypeOption(javaTypeOption);
        return when;
    }
}
