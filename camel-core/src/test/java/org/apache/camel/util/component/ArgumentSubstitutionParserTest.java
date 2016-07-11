/**
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
package org.apache.camel.util.component;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.util.component.ArgumentSubstitutionParser.Substitution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ArgumentSubstitutionParserTest {

    private static final String PERSON = "person";

    @Test
    public void testParse() throws Exception {

        final Substitution[] adapters = new Substitution[4];
        adapters[0] = new Substitution(".+", "name", PERSON);
        adapters[1] = new Substitution("greet.+", "person([0-9]+)", "astronaut$1");
        adapters[2] = new Substitution(".+", "(.+)", "java.util.List", "$1List");
        adapters[3] = new Substitution(".+", "(.+)", ".*?(\\w++)\\[\\]", "$1Array", true);

        final ApiMethodParser<TestProxy> parser = new ArgumentSubstitutionParser<TestProxy>(TestProxy.class, adapters);

        final ArrayList<String> signatures = new ArrayList<String>();
        signatures.add("public String sayHi();");
        signatures.add("public String sayHi(final String name);");
        signatures.add("public final String greetMe(final String name);");
        signatures.add("public final String greetUs(final String name1, String name2);");
        signatures.add("public final String greetAll(String[] names);");
        signatures.add("public final String greetAll(java.util.List<String> names);");
        signatures.add("public final java.util.Map<String, String> greetAll(java.util.Map<String> nameMap);");
        signatures.add("public final String[] greetTimes(String name, int times);");
        signatures.add("public final String greetInnerChild(org.apache.camel.util.component.TestProxy.InnerChild child);");
        parser.setSignatures(signatures);

        final List<ApiMethodParser.ApiMethodModel> methodModels = parser.parse();
        assertEquals(9, methodModels.size());

        final ApiMethodParser.ApiMethodModel sayHi1 = methodModels.get(8);
        assertEquals(PERSON, sayHi1.getArguments().get(0).getName());
        assertEquals("SAYHI_1", sayHi1.getUniqueName());

        final ApiMethodParser.ApiMethodModel greetMe = methodModels.get(4);
        assertEquals(PERSON, greetMe.getArguments().get(0).getName());

        final ApiMethodParser.ApiMethodModel greetUs = methodModels.get(6);
        assertEquals("astronaut1", greetUs.getArguments().get(0).getName());
        assertEquals("astronaut2", greetUs.getArguments().get(1).getName());

        final ApiMethodParser.ApiMethodModel greetAll = methodModels.get(0);
        assertEquals("personMap", greetAll.getArguments().get(0).getName());

        final ApiMethodParser.ApiMethodModel greetAll1 = methodModels.get(1);
        assertEquals("personsList", greetAll1.getArguments().get(0).getName());

        final ApiMethodParser.ApiMethodModel greetAll2 = methodModels.get(2);
        assertEquals("stringArray", greetAll2.getArguments().get(0).getName());

        final ApiMethodParser.ApiMethodModel greetInnerChild = methodModels.get(3);
        assertEquals("child", greetInnerChild.getArguments().get(0).getName());
    }

}
