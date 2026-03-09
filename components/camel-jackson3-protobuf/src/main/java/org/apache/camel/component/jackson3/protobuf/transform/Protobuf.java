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
package org.apache.camel.component.jackson3.protobuf.transform;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.dataformat.protobuf.ProtobufMapper;

public final class Protobuf {

    private static final ProtobufMapper MAPPER;

    static {
        MAPPER = ProtobufMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(EnumFeature.READ_ENUMS_USING_TO_STRING)
                .enable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
                .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                .changeDefaultPropertyInclusion(
                        value -> value.withValueInclusion(JsonInclude.Include.NON_EMPTY)
                                .withContentInclusion(JsonInclude.Include.NON_EMPTY))
                .build();
    }

    private Protobuf() {
        // prevent instantiation of utility class
    }

    /**
     * Provides access to the default object mapper instance.
     *
     * @return the default object mapper.
     */
    public static ProtobufMapper mapper() {
        return MAPPER;
    }
}
