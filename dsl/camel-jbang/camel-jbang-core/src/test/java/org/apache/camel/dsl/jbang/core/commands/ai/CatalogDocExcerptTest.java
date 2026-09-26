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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25040: a component's answer carries the start of its documentation page, because the option list can only say
 * what can be set. That a named parameter of the sql component is written {@code :#name} is not an option and appeared
 * nowhere in the answer, only in a page an author who does not know the answer has no reason to ask for.
 */
class CatalogDocExcerptTest {

    private static JsonObject catalogDoc(Map<String, Object> args) throws Exception {
        Map<String, String> stringArgs = new HashMap<>();
        args.forEach((k, v) -> stringArgs.put(k, String.valueOf(v)));
        String json = String.valueOf(ToolRegistry.execute("camel_catalog_doc", new ToolContext(), stringArgs));
        return (JsonObject) Jsoner.deserialize(json);
    }

    @Test
    public void testTheSqlNamedParameterSyntaxIsInTheAnswer() throws Exception {
        JsonObject answer = catalogDoc(Map.of("name", "sql"));
        String documentation = answer.getString("documentation");
        assertNotNull(documentation, "no documentation in: " + answer.toJson());

        // the whole point: the syntax that is not an option
        assertTrue(documentation.contains(":#name_of_the_parameter") || documentation.contains(":#myId"),
                "the named parameter syntax is not in the excerpt:\n" + documentation);
        assertNotNull(answer.getString("documentationHint"));
    }

    @Test
    public void testTheExcerptIsProseAndNotAsciiDocPlumbing() throws Exception {
        String documentation = catalogDoc(Map.of("name", "sql")).getString("documentation");

        // the generated option tables are the answer's own options, not prose
        assertFalse(documentation.contains("include::"), documentation);
        // a cross-reference reads as the words it links
        assertFalse(documentation.contains("xref:"), documentation);
        // the title and attribute header of the page are not carried
        assertFalse(documentation.contains(":doctitle:"), documentation);
        assertFalse(documentation.contains(":artifactid:"), documentation);
    }

    @Test
    public void testTheExcerptStaysWithinItsBudget() throws Exception {
        for (String name : new String[] { "sql", "kafka", "timer", "file", "http" }) {
            String documentation = catalogDoc(Map.of("name", name)).getString("documentation");
            if (documentation != null) {
                assertTrue(documentation.length() <= CatalogDocs.DOC_EXCERPT_BUDGET,
                        name + " excerpt is " + documentation.length() + " chars");
            }
        }
    }

    @Test
    public void testAskingForTheWholePageDoesNotAlsoSendTheExcerpt() throws Exception {
        JsonObject answer = catalogDoc(Map.of("name", "sql", "includeDoc", "true"));
        assertNotNull(answer.getString("doc"));
        assertNull(answer.getString("documentation"), "the whole page is there, so the excerpt would repeat it");
    }

    @Test
    public void testAComponentWithNoPageIsStillAnswered() {
        // a page is not guaranteed; the answer must not depend on one
        assertNull(CatalogDocs.docExcerpt(null, CatalogDocs.DOC_EXCERPT_BUDGET));
        assertNull(CatalogDocs.docExcerpt("", CatalogDocs.DOC_EXCERPT_BUDGET));
        assertNull(CatalogDocs.docExcerpt("= Title\n:shortname: x\n\nIntro with no sections.\n",
                CatalogDocs.DOC_EXCERPT_BUDGET));
    }

    @Test
    public void testTheExcerptStartsAtTheFirstSection() {
        String page = """
                = SQL Component
                :doctitle: SQL
                :shortname: sql

                *Since Camel 1.4*

                Intro prose that the description already says.

                [source,xml]
                ----
                <dependency>camel-sql</dependency>
                ----

                == URI format

                Use :#name for a named parameter.
                """;
        String excerpt = CatalogDocs.docExcerpt(page, CatalogDocs.DOC_EXCERPT_BUDGET);
        assertTrue(excerpt.startsWith("== URI format"), excerpt);
        assertTrue(excerpt.contains("Use :#name for a named parameter."), excerpt);
        assertFalse(excerpt.contains("camel-sql</dependency>"), excerpt);
        assertFalse(excerpt.contains("Intro prose"), excerpt);
    }

    @Test
    public void testCodeInsideAFenceIsKept() {
        String page = """
                == URI format

                ----
                sql:select * from table where id=:#myId order by name[?options]
                ----
                """;
        String excerpt = CatalogDocs.docExcerpt(page, CatalogDocs.DOC_EXCERPT_BUDGET);
        assertTrue(excerpt.contains("sql:select * from table where id=:#myId"), excerpt);
    }
}
