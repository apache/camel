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
package org.apache.camel.component.spring.jdbc;

import javax.sql.DataSource;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.component.jdbc.JdbcEndpoint;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;

/**
 * Access databases through SQL and JDBC with Spring Transaction support.
 */
@UriEndpoint(firstVersion = "3.10.0", scheme = "spring-jdbc", title = "Spring JDBC", syntax = "spring-jdbc:dataSourceName",
             producerOnly = true,
             category = { Category.DATABASE })
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  description = "The SQL statement as text, or bytes, a stream or a file of it; a Map or a List"
                                                + " is not a statement: set the SQL as the body. With useHeadersAsParameters,"
                                                + " the values of its placeholders come from the CamelJdbcParameters header, or"
                                                + " else from the headers"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.UNCHANGED }, dependsOn = "outputClass",
                                   byOption = {
                                           @BodyType.When(option = "outputType", value = "SelectOne",
                                                          carriers = {
                                                                  Carrier.MAP, Carrier.TEXT, Carrier.VALUE, Carrier.BYTES,
                                                                  Carrier.POJO, Carrier.NULL, Carrier.UNCHANGED }),
                                           @BodyType.When(option = "outputType", value = "StreamList",
                                                          carriers = { Carrier.ITERATOR, Carrier.UNCHANGED }) },
                                   description = "The rows of a select: a List of Maps of the column names and their values"
                                                 + " (beans of outputClass when set). SelectOne gives the Map of the one row,"
                                                 + " the value of its column as the driver reads it when it has one, or null"
                                                 + " for no row; StreamList an Iterator of the rows, read once. A statement"
                                                 + " that is not a select leaves the body unchanged, with the count in the"
                                                 + " CamelJdbcUpdateCount header"))
public class SpringJdbcEndpoint extends JdbcEndpoint {

    public SpringJdbcEndpoint() {
    }

    public SpringJdbcEndpoint(String endpointUri, Component component, DataSource dataSource) {
        super(endpointUri, component, dataSource);
    }
}
