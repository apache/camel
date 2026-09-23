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
package org.apache.camel.language.simple;

import org.apache.camel.LanguageTestSupport;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CAMEL-24965: edge cases of the init block.
 */
public class SimpleInitBlockEdgeCasesTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    @Test
    public void testCommentedOutAssignmentDoesNotRun() {
        assertExpression("""
                $init{
                  // $foo := 'x';
                  $bar := 'y';
                }init$
                [${variable.foo}][$bar]""", "[][y]");
    }

    @Test
    public void testCommentWithDollar() {
        assertExpression("""
                $init{
                  // costs $5
                  $bar := 'y'; // a trailing comment
                }init$
                [$bar]""", "[y]");
    }

    @Test
    public void testCommentInsideQuotesIsText() {
        assertExpression("""
                $init{
                  $url := 'http://camel.apache.org';
                }init$
                $url""", "http://camel.apache.org");
    }

    @Test
    public void testOneLiner() {
        assertExpression("$init{ $minAge := 18; }init$[$minAge]", "[18]");
        assertExpression("$init{ $a := 'x'; $b := 'y'; }init$[$a$b]", "[xy]");
        // a ; inside a function or quotes is not the end of the statement
        assertExpression("$init{ $a := 'x; y'; }init$[$a]", "[x; y]");
    }

    @Test
    public void testWindowsLineEndings() {
        assertExpression("$init{\r\n  $a := 'x';\r\n  $b := 'y';\r\n}init$\r\n[$a$b]", "[xy]");
    }

    @Test
    public void testPredicateWithOnlyFunctions() {
        exchange.getMessage().setBody(" A ");
        assertPredicate("""
                $init{
                  $clean ~:= ${trim()};
                }init$
                $clean() == 'A'""", true);
    }

    @Test
    public void testCustomFunctionIsLocalToTheExpression() {
        exchange.getMessage().setBody("  abc  ");
        assertExpression("""
                $init{
                  $f ~:= ${trim()};
                }init$
                [$f()]""", "[abc]");
        assertExpression("""
                $init{
                  $f ~:= ${uppercase()};
                }init$
                [$f()]""", "[  ABC  ]");
    }

    @Test
    public void testErrorPointsIntoTheInitBlock() {
        String text = "$init{\n  $a = 5;\n}init$";
        SimpleIllegalSyntaxException e = assertThrows(SimpleIllegalSyntaxException.class,
                () -> context.resolveLanguage("simple").createExpression(text));
        // the error is at the = which is not an init operator
        assertEquals(text.indexOf('='), e.getIndex());
    }
}
