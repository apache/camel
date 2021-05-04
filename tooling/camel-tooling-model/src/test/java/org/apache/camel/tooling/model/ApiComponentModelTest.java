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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ApiComponentModelTest {

    @Test
    public void loadTwilioSchema() throws Exception {
        InputStream is = ApiComponentModelTest.class.getClassLoader().getResourceAsStream("twilio.json");
        String json = loadText(is);
        ComponentModel model = JsonMapper.generateComponentModel(json);

        Assertions.assertNotNull(model);
        Assertions.assertTrue(model.isApi());
        Assertions.assertEquals("apiName/methodName", model.getApiSyntax());
        Assertions.assertEquals(56, model.getApiOptions().size());
        ApiModel am = model.getApiOptions().stream().filter(a -> a.getName().equals("call")).findFirst().orElse(null);
        Assertions.assertNotNull(am);
        Assertions.assertEquals("call", am.getName());
        Assertions.assertEquals("", am.getDescription());
        Assertions.assertEquals(5, am.getAliases().size());
        Assertions.assertEquals("^creator$=create", am.getAliases().get(0));
        ApiMethodModel amm = am.getMethods().stream().filter(a -> a.getName().equals("creator")).findFirst().orElse(null);
        Assertions.assertNotNull(amm);
        Assertions.assertEquals("creator", amm.getName());
        Assertions.assertEquals("Create a CallCreator to execute create", amm.getDescription());
        Assertions.assertEquals(6, amm.getSignatures().size());

        Map<String, Object> md = model.getMetadata();
        Assertions.assertNotNull(md);
        Assertions.assertEquals("foo", md.get("string"));
        Assertions.assertEquals(BigDecimal.valueOf(42), md.get("number"));
        Assertions.assertEquals(Boolean.TRUE, md.get("boolean"));
        Assertions.assertEquals(Arrays.asList("bar", "baz"), md.get("list"));
        Assertions.assertEquals(new LinkedHashMap<String, Object>() {
            {
                put("k1", "v1");
                put("k2", "v2");
            }
        }, md.get("map"));

        String serialized = JsonMapper.createParameterJsonSchema(model);
        ComponentModel reloadedModel = JsonMapper.generateComponentModel(serialized);
        Assertions.assertEquals(model.getMetadata(), reloadedModel.getMetadata());
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    private static String loadText(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        InputStreamReader isr = new InputStreamReader(in);
        try {
            BufferedReader reader = new BufferedReader(isr);
            while (true) {
                String line = reader.readLine();
                if (line != null) {
                    builder.append(line);
                    builder.append("\n");
                } else {
                    break;
                }
            }
            return builder.toString();
        } finally {
            isr.close();
            in.close();
        }
    }

}
