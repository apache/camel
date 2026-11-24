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
package org.apache.camel.component.file;

import java.nio.file.Files;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The include and exclude options are regular expressions: a wildcard such as *.xml is not one, and the endpoint fails
 * with a message that says so and names the options for wildcards and extensions.
 */
class FileConsumerIncludeExcludeNotRegexTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void includeWithWildcardFailsWithRegexHint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?include=*.xml")).to("mock:result");
            }
        });

        FailedToCreateRouteException e = assertThrows(FailedToCreateRouteException.class, () -> context.start());

        assertTrue(e.getMessage().endsWith(
                "with value: *.xml: Dangling meta character '*' near index 0 in include=*.xml: the include option is a"
                                           + " regular expression matched against the file name, such as .*\\.xml."
                                           + " For wildcards such as *.xml use the antInclude option, or includeExt to"
                                           + " match the file extension."),
                e.getMessage());
    }

    @Test
    void excludeWithWildcardFailsWithRegexHint() {
        ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint(fileUri("?exclude=*.bak")));

        assertTrue(e.getMessage().endsWith(
                "with value: *.bak: Dangling meta character '*' near index 0 in exclude=*.bak: the exclude option is a"
                                           + " regular expression matched against the file name, such as .*\\.xml."
                                           + " For wildcards such as *.xml use the antExclude option, or excludeExt to"
                                           + " match the file extension."),
                e.getMessage());
    }

    @Test
    void includeWithRegexConsumesTheFilesThatMatch() throws Exception {
        Files.writeString(testFile("a.xml"), "<a/>");
        Files.writeString(testFile("b.txt"), "b");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?include=.*\\.xml&noop=true&initialDelay=0&delay=10")).to("mock:result");
            }
        });
        context.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.expectedHeaderReceived(Exchange.FILE_NAME, "a.xml");
        MockEndpoint.assertIsSatisfied(context);
    }
}
