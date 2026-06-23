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

import java.io.File;

import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.model.StepDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.Node;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;

public class CamelYamlParserTest {

    private static CamelYamlParser parser;

    @BeforeAll
    public static void setup() throws Exception {
        parser = new CamelYamlParser();
    }

    @Test
    public void testParseOk() throws Exception {
        Assertions.assertTrue(parser.parse(new File("src/test/resources/foo.yaml")).isEmpty());
    }

    @Test
    public void testParseOkPlaceholder() throws Exception {
        Assertions.assertTrue(parser.parse(new File("src/test/resources/foo2.yaml")).isEmpty());
    }

    @Test
    public void testParseRuntimeCustomStep() throws Exception {
        Assertions.assertTrue(parser.parse(new File("src/test/resources/custom-parser-step.yaml")).isEmpty());
    }

    @Test
    public void testParseBad() throws Exception {
        var report = parser.parse(new File("src/test/resources/bad.yaml"));
        Assertions.assertFalse(report.isEmpty());
        Assertions.assertEquals(1, report.size());
        Assertions.assertTrue(report.get(0).getMessage().contains("Unknown node id: setCheese"));
        Assertions.assertTrue(report.get(0).getMessage().contains("- setCheese:"));
    }

    @Test
    public void testParseBadPlaceholder() throws Exception {
        var report = parser.parse(new File("src/test/resources/bad2.yaml"));
        Assertions.assertFalse(report.isEmpty());
        Assertions.assertEquals(1, report.size());
        Assertions.assertTrue(report.get(0).getMessage().contains("Unknown node id: setCheese"));
        Assertions.assertTrue(report.get(0).getMessage().contains("- setCheese:"));
    }

    public static final class ParserStepResolver implements YamlDeserializerResolver {
        @Override
        public ConstructNode resolve(String id) {
            if ("parserStep".equals(id)) {
                return new ParserStepDeserializer();
            }
            return null;
        }
    }

    static final class ParserStepDeserializer extends YamlDeserializerBase<StepDefinition> {
        ParserStepDeserializer() {
            super(StepDefinition.class);
        }

        @Override
        protected StepDefinition newInstance() {
            return new StepDefinition();
        }

        @Override
        protected boolean setProperty(StepDefinition target, String propertyKey, String propertyName, Node value) {
            switch (propertyKey) {
                case "id":
                    target.setId(asText(value));
                    return true;
                case "steps":
                    setSteps(target, value);
                    return true;
                default:
                    return false;
            }
        }
    }
}
