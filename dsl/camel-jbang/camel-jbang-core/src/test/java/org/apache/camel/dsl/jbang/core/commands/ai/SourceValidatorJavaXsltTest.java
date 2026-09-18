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

import java.util.List;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24698: Java, XSLT and XML are checked the way camel run would compile or parse them, before the file is
 * written.
 */
public class SourceValidatorJavaXsltTest {

    @Test
    void javaThatDoesNotCompileIsReportedWithTheLine() {
        List<String> msgs = SourceValidator.validateJava("MyAggregator.java", """
                package com.example;

                import org.apache.camel.AggregationStrategy;
                import org.apache.camel.Exchange;

                public class MyAggregator implements AggregationStrategy {
                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        String s = oldExchange.getIn().getBody(String.class) + newExchange.getIn().getBody(String.class)
                        return newExchange;
                    }
                }
                """);
        assertThat(msgs).isNotEmpty();
        assertThat(msgs.get(0)).startsWith("Line 8:").contains("';' expected");
    }

    @Test
    void proseAfterTheClassIsNamed() {
        List<String> msgs = SourceValidator.validateJava("MyAggregator.java", """
                package com.example;

                import org.apache.camel.AggregationStrategy;
                import org.apache.camel.Exchange;

                public class MyAggregator implements AggregationStrategy {
                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        return newExchange;
                    }
                }

                ## How it works
                The strategy keeps the newest exchange, run it with camel run.
                """);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).startsWith("Line 12 is not Java (\"## How it works\")").contains("// comment");
    }

    @Test
    void oldExchangeUsedInsideItsNullBranchIsNamed() {
        List<String> msgs = SourceValidator.validateJava("BatchAggregator.java", """
                import org.apache.camel.AggregationStrategy;
                import org.apache.camel.Exchange;

                public class BatchAggregator implements AggregationStrategy {
                    @Override
                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        if (oldExchange == null) {
                            String body = newExchange.getIn().getBody(String.class);
                            oldExchange.getIn().setBody(body);
                            return oldExchange;
                        }
                        oldExchange.getIn().setBody(oldExchange.getIn().getBody(String.class) + newExchange.getIn().getBody());
                        return oldExchange;
                    }
                }
                """);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).startsWith("Line 9: oldExchange is null inside if (oldExchange == null)")
                .contains("NullPointerException").contains("return newExchange");
    }

    @Test
    void oldExchangeAssignedInItsNullBranchIsFine() {
        List<String> msgs = SourceValidator.validateJava("BatchAggregator.java", """
                import org.apache.camel.AggregationStrategy;
                import org.apache.camel.Exchange;

                public class BatchAggregator implements AggregationStrategy {
                    @Override
                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        if (oldExchange == null) {
                            oldExchange = newExchange;
                            oldExchange.getIn().setBody(newExchange.getIn().getBody(String.class));
                            return oldExchange;
                        }
                        return oldExchange;
                    }
                }
                """);
        assertThat(msgs).isEmpty();
    }

    @Test
    void javaThatCompilesAgainstCamelIsFine() {
        List<String> msgs = SourceValidator.validateJava("MyAggregator.java", """
                package com.example;

                import org.apache.camel.AggregationStrategy;
                import org.apache.camel.Exchange;

                public class MyAggregator implements AggregationStrategy {
                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        return newExchange;
                    }
                }
                """);
        assertThat(msgs).isEmpty();
    }

    @Test
    void proseAfterTheStylesheetIsNamed() {
        List<String> msgs = SourceValidator.validateXslt("""
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/">
                    <report><xsl:apply-templates/></report>
                  </xsl:template>
                </xsl:stylesheet>

                ## What this does

                The stylesheet wraps the input in a report element.
                """);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("not allowed in trailing section")
                .contains("it continues with \"## What this does\"").contains("<!-- XML comment -->")
                .doesNotContain("JDK processor");
    }

    @Test
    void xslt2FunctionInA10StylesheetPointsToSaxon() {
        org.junit.jupiter.api.Assumptions.assumeTrue(XmlChecks.saxonFactory() == null, "JDK processor only");
        List<String> msgs = SourceValidator.validateXslt("""
                <?xml version="1.0"?>
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/">
                    <out><xsl:value-of select="current-dateTime()"/></out>
                  </xsl:template>
                </xsl:stylesheet>
                """);
        assertThat(msgs).isNotEmpty();
        assertThat(msgs.get(0)).startsWith("XSLT:").contains("current-dateTime").contains("xslt-saxon");
    }

    @Test
    void xslt2StylesheetWithoutSaxonIsOnlyCheckedForWellFormedXml() {
        org.junit.jupiter.api.Assumptions.assumeTrue(XmlChecks.saxonFactory() == null, "JDK processor only");
        assertThat(SourceValidator.validateXslt("""
                <?xml version="1.0"?>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/"><out><xsl:value-of select="current-dateTime()"/></out></xsl:template>
                </xsl:stylesheet>
                """)).isEmpty();
    }

    @Test
    void textBeforeTheStylesheetIsNamed() {
        List<String> msgs = SourceValidator.validateXslt("""
                xslt
                <?xml version="1.0"?>
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/"><out/></xsl:template>
                </xsl:stylesheet>
                """);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("not allowed in prolog").contains("must start with <?xml")
                .contains("the file starts with \"xslt\"").doesNotContain("xslt-saxon");
    }

    @Test
    void validXsltAndXmlAreFine() {
        assertThat(SourceValidator.validateXslt("""
                <?xml version="1.0"?>
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/"><out><xsl:value-of select="/a/b"/></out></xsl:template>
                </xsl:stylesheet>
                """)).isEmpty();
        assertThat(SourceValidator.validateXml("<order><id>1</id></order>")).isEmpty();
    }

    @Test
    void malformedXmlIsReportedWithTheLine() {
        List<String> msgs = SourceValidator.validateXml("<order>\n  <id>1</order>");
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).startsWith("Line 2: XML is not well formed");
    }

    @Test
    void theFileKindsAreRoutedByExtension() {
        assertThat(SourceValidator.isValidatableFile("Foo.java")).isTrue();
        assertThat(SourceValidator.isValidatableFile("t.xsl")).isTrue();
        assertThat(SourceValidator.isValidatableFile("in.xml")).isTrue();
        assertThat(SourceValidator.isValidatableFile("README.md")).isFalse();
        assertThat(SourceValidator.validate("t.xsl", "<not-xslt/>", new DefaultCamelCatalog(), null)).isNotEmpty();
    }

    @Test
    void proseAfterAnXmlFileIsNamed() {
        List<String> msgs = SourceValidator.validateXml("""
                <?xml version="1.0"?>
                <books><book id="1">A</book></books>

                This file is read by the route every 5 seconds.
                """);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("not allowed in trailing section")
                .contains("it continues with \"This file is read by the route every 5 s...\"").contains("XML comment");
    }

    @Test
    void aClassNamedLikeItsImportedInterfaceIsNamed() {
        List<String> msgs = SourceValidator.validateJava("AggregationStrategy.java", """
                import org.apache.camel.AggregationStrategy;
                import org.apache.camel.Exchange;

                public class AggregationStrategy implements AggregationStrategy {
                    public Exchange aggregate(Exchange a, Exchange b) {
                        return b;
                    }
                }
                """);
        assertThat(msgs).isNotEmpty();
        assertThat(msgs.get(0)).contains("already defined in this compilation unit")
                .contains("rename the class, for example MyAggregationStrategy");
    }

    @Test
    void aMessageMethodCalledOnTheExchangeIsNamed() {
        List<String> msgs = SourceValidator.validateJava("NameFormatter.java", """
                import org.apache.camel.Exchange;

                public class NameFormatter {
                    public void format(Exchange exchange) {
                        exchange.setHeader("name", "x");
                    }
                }
                """);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("cannot find symbol").contains("Exchange has no setHeader")
                .contains("exchange.getMessage().setHeader(...)").doesNotContain("//DEPS");
    }
}
