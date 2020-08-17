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
package org.apache.camel.component.jooq;

import java.nio.file.Files;
import java.sql.Connection;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;

@CamelSpringTest
@ContextConfiguration(locations = { "/jooq-spring.xml" })
public abstract class BaseJooqTest extends CamelTestSupport {

    @Autowired
    DSLContext create;

    @Value("classpath:db-hsql.sql")
    Resource ddlScriptFile;

    @BeforeEach
    public void init() throws Exception {
        String sql = new String(Files.readAllBytes(ddlScriptFile.getFile().toPath()));
        Connection conn = create.configuration().connectionProvider().acquire();
        conn.createStatement().execute(sql);
    }
}
