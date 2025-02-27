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
package org.apache.camel.spring.interceptor;

import javax.sql.DataSource;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Used for unit testing that we can use spring and camel annotations mixed together
 */
@Transactional
@Service
public class AnnotatedBookServiceImpl implements AnnotatedBookStore {

    @Autowired
    private DataSource dataSource;

    @EndpointInject("seda:book")
    private ProducerTemplate template;

    @Override
    public void orderBook(String title) throws Exception {
        Transactional tx = this.getClass().getAnnotation(Transactional.class);
        if (tx == null) {
            throw new IllegalStateException("Spring annotation-driven should have instrumented this class as @Transactional");
        }
        if (!"REQUIRED".equals(tx.propagation().name())) {
            throw new IllegalStateException("Should be REQUIRED propagation");
        }

        if (title.startsWith("Donkey")) {
            throw new IllegalArgumentException("We don't have Donkeys, only Camels");
        }

        // create new local datasource to store in DB
        new JdbcTemplate(dataSource).update("insert into books (title) values (?)", title);

        template.sendBody(title);
    }
}
