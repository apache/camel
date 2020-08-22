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
package org.apache.camel.component.dozer;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class DozerComponentTest extends CamelTestSupport {

    private static final String NAME = "examplename";
    private static final String MARSHAL_ID = "marshal123";
    private static final String UNMARSHAL_ID = "unmarshal456";
    private static final String SOURCE_MODEL = "org.example.A";
    private static final String TARGET_MODEL = "org.example.B";
    private static final String DOZER_CONFIG_PATH = "mapping.xml";
    private static final String TRANSFORM_EP_1 = "dozer:" + NAME
                                                 + "?marshalId=" + MARSHAL_ID
                                                 + "&unmarshalId=" + UNMARSHAL_ID
                                                 + "&sourceModel=" + SOURCE_MODEL
                                                 + "&targetModel=" + TARGET_MODEL
                                                 + "&mappingFile=" + DOZER_CONFIG_PATH;

    @Test
    void testCreateEndpoint() {
        DozerEndpoint ep = context.getEndpoint(TRANSFORM_EP_1, DozerEndpoint.class);
        DozerConfiguration config = ep.getConfiguration();
        assertEquals(NAME, config.getName());
        assertEquals(MARSHAL_ID, config.getMarshalId());
        assertEquals(UNMARSHAL_ID, config.getUnmarshalId());
        assertEquals(SOURCE_MODEL, config.getSourceModel());
        assertEquals(TARGET_MODEL, config.getTargetModel());
        assertEquals(DOZER_CONFIG_PATH, config.getMappingFile());
    }

    @Test
    void requiredTargetModelMissing() {
        try {
            context.getEndpoint("dozer:noTargetModel?mappingFile=mapping.xml", DozerEndpoint.class);
            fail("targetModel is a required parameter");
        } catch (Exception ex) {
            // expected
        }
    }
}
