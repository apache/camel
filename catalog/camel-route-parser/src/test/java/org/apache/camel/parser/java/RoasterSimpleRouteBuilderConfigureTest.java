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
package org.apache.camel.parser.java;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

import org.apache.camel.parser.ParserResult;
import org.apache.camel.parser.helper.CamelJavaParserHelper;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoasterSimpleRouteBuilderConfigureTest {

    private static final Logger LOG = LoggerFactory.getLogger(RoasterSimpleRouteBuilderConfigureTest.class);

    @Test
    public void parse() throws Exception {
        JavaClassSource clazz = (JavaClassSource) Roaster.parse(new File("src/test/java/org/apache/camel/parser/java/MySimpleRouteBuilder.java"));
        MethodSource<JavaClassSource> method = clazz.getMethod("configure");

        List<ParserResult> list = CamelJavaParserHelper.parseCamelSimpleExpressions(method);
        for (ParserResult simple : list) {
            LOG.info("Simple: " + simple.getElement());
            LOG.info("  Line: " + findLineNumber(simple.getPosition()));
        }
        Assert.assertEquals("${body} > 100", list.get(0).getElement());
        Assert.assertEquals(true, list.get(0).getPredicate());
        Assert.assertEquals(27, findLineNumber(list.get(0).getPosition()));
        Assert.assertEquals("${body} > 200", list.get(1).getElement());
        Assert.assertEquals(true, list.get(1).getPredicate());
        Assert.assertEquals(30, findLineNumber(list.get(1).getPosition()));
    }

    public static int findLineNumber(int pos) throws Exception {
        int lines = 0;
        int current = 0;
        File file = new File("src/test/java/org/apache/camel/parser/java/MySimpleRouteBuilder.java");
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines++;
                current += line.length();
                if (current > pos) {
                    return lines;
                }
            }
        }
        return -1;
    }

}
