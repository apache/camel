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
package org.apache.camel.language.jq;

import java.util.Objects;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class JqExpressionPojoTest extends JqTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = super.createCamelContext();
        answer.getGlobalOptions().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
        answer.getGlobalOptions().put(JacksonConstants.TYPE_CONVERTER_TO_POJO, "true");

        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .transform().jq(".book", Book.class)
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testExpression() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(new Book("foo", "bar"));

        ObjectNode node = MAPPER.createObjectNode();
        node.with("book").put("author", "foo").put("title", "bar");

        template.sendBody("direct:start", node);

        MockEndpoint.assertIsSatisfied(context);
    }

    public static class Book {
        String author;
        String title;

        public Book() {
        }

        public Book(String author, String title) {
            this.author = author;
            this.title = title;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Book)) {
                return false;
            }
            Book book = (Book) o;
            return Objects.equals(getAuthor(), book.getAuthor()) && Objects.equals(getTitle(), book.getTitle());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getAuthor(), getTitle());
        }
    }
}
