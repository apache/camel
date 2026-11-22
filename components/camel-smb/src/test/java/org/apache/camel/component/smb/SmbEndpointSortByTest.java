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
package org.apache.camel.component.smb;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The smb endpoint parses sortBy and checks the consumer options like the file component.
 */
class SmbEndpointSortByTest extends CamelTestSupport {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "file:name                         | B.txt,a.txt,c.dat,d.txt",
            "reverse:file:name                 | d.txt,c.dat,a.txt,B.txt",
            "ignoreCase:file:name              | a.txt,B.txt,c.dat,d.txt",
            "reverse:ignoreCase:file:name      | d.txt,c.dat,B.txt,a.txt",
            "file:ext;reverse:ignoreCase:file:name | c.dat,d.txt,B.txt,a.txt" })
    void sortBy(String sortBy, String expected) {
        GenericFileEndpoint<?> endpoint
                = context.getEndpoint("smb:localhost/share?sortBy=" + sortBy, GenericFileEndpoint.class);

        List<Exchange> exchanges = new ArrayList<>();
        for (String name : List.of("c.dat", "a.txt", "d.txt", "B.txt")) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setHeader(Exchange.FILE_NAME, name);
            exchanges.add(exchange);
        }
        exchanges.sort(endpoint.getSortBy());

        assertEquals(List.of(expected.split(",")),
                exchanges.stream().map(e -> e.getMessage().getHeader(Exchange.FILE_NAME, String.class)).toList());
    }

    @Test
    void moveWithDeleteFails() {
        GenericFileEndpoint<?> endpoint
                = context.getEndpoint("smb:localhost/share?delete=true&move=done", GenericFileEndpoint.class);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> endpoint.createConsumer(exchange -> {
        }));
        assertEquals("You cannot set both delete=true and move options", e.getMessage());
    }
}
