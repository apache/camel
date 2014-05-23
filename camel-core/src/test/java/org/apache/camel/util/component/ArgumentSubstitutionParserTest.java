package org.apache.camel.util.component;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import static org.apache.camel.util.component.ArgumentSubstitutionParser.*;
import static org.junit.Assert.assertEquals;

public class ArgumentSubstitutionParserTest {

    private static final String PERSON = "person";

    @Test
    public void testParse() throws Exception {

        final Substitution[] adapters = new Substitution[3];
        adapters[0] = new Substitution(".+", "name", PERSON);
        adapters[1] = new Substitution("greet.+", "person([0-9]+)", "astronaut$1");
        adapters[2] = new Substitution(".+", "(.+)", "java.util.List", "$1List");

        final ApiMethodParser<TestProxy> parser = new ArgumentSubstitutionParser<TestProxy>(TestProxy.class, adapters);

        final ArrayList<String> signatures = new ArrayList<String>();
        signatures.add("public String sayHi();");
        signatures.add("public String sayHi(final String name);");
        signatures.add("public final String greetMe(final String name);");
        signatures.add("public final String greetUs(final String name1, String name2);");
        signatures.add("public final String greetAll(String[] names);");
        signatures.add("public final String greetAll(java.util.List<String> names);");
        signatures.add("public final String[] greetTimes(String name, int times);");
        parser.setSignatures(signatures);

        final List<ApiMethodParser.ApiMethodModel> methodModels = parser.parse();
        assertEquals(7, methodModels.size());

        final ApiMethodParser.ApiMethodModel sayHi1 = methodModels.get(6);
        assertEquals(PERSON, sayHi1.getArguments().get(0).getName());
        assertEquals("SAYHI_1", sayHi1.getUniqueName());

        final ApiMethodParser.ApiMethodModel greetMe = methodModels.get(2);
        assertEquals(PERSON, greetMe.getArguments().get(0).getName());

        final ApiMethodParser.ApiMethodModel greetUs = methodModels.get(4);
        assertEquals("astronaut1", greetUs.getArguments().get(0).getName());
        assertEquals("astronaut2", greetUs.getArguments().get(1).getName());

        final ApiMethodParser.ApiMethodModel greetAll = methodModels.get(1);
        assertEquals("personsList", greetAll.getArguments().get(0).getName());
    }

}
