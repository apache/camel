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
package org.apache.camel.component.netty.http.handlers;

import org.apache.camel.component.netty.http.NettyHttpConsumer;
import org.apache.camel.component.netty.http.NettyHttpEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The roles of a security constraint are a comma separated list of role names, and the user is in role when one of the
 * user roles is equal to one of those names.
 */
class HttpServerChannelHandlerRolesTest extends CamelTestSupport {

    private HttpServerChannelHandler handler;

    @BeforeEach
    void createHandler() {
        NettyHttpEndpoint endpoint = context.getEndpoint("netty-http:http://localhost:8080/roles", NettyHttpEndpoint.class);
        handler = new HttpServerChannelHandler(new NettyHttpConsumer(endpoint, exchange -> {
        }, endpoint.getConfiguration()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
            # roles           | user roles       | in role
            *                 | guest            | true
            *                 | null             | true
            admin             | admin,guest      | true
            admin,guest       | guest            | true
            'admin, guest'    | guest            | true
            ' admin , guest ' | 'guest '         | true
            admins            | admin            | false
            readwrite         | read             | false
            admin,guest       | dmin             | false
            Admin             | admin            | false
            admin;guest       | guest            | false
            admin             | 'guest,,viewer'  | false
            'admin, ops'      | 'guest, ,viewer' | false
            'admin,,ops'      | 'guest,,viewer'  | false
            admin             | null             | false
            """)
    void matchesRoles(String roles, String userRoles, boolean inRole) {
        assertEquals(inRole, handler.matchesRoles(roles, userRoles), () -> "roles " + roles + ", user roles " + userRoles);
    }
}
