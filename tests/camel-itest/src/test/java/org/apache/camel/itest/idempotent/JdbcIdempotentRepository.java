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
package org.apache.camel.itest.idempotent;

import javax.sql.DataSource;

import org.apache.camel.spi.IdempotentRepository;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcIdempotentRepository implements IdempotentRepository {

    private JdbcTemplate jdbc;

    public void setDataSource(DataSource ds) {
        this.jdbc = new JdbcTemplate(ds);
    }

    @Override
    public boolean add(String key) {
        // check we already have it because eager option can have been turned on
        if (contains(key)) {
            return false;
        }

        jdbc.update("INSERT INTO ProcessedPayments (paymentIdentifier) VALUES (?)", key);
        return true;
    }

    @Override
    public boolean contains(String key) {
        int numMatches = jdbc.queryForObject("SELECT count(0) FROM ProcessedPayments where paymentIdentifier = ?", Integer.class, key);
        return numMatches > 0;
    }

    @Override
    public boolean remove(String key) {
        jdbc.update("DELETE FROM ProcessedPayments WHERE paymentIdentifier = ?", key);
        return true;
    }

    @Override
    public boolean confirm(String key) {
        return true;
    }
    
    @Override
    public void clear() {
        jdbc.update("DELETE * FROM ProcessedPayments");
    }

    @Override
    public void start() {
        // noop
    }

    @Override
    public void stop() {
        // noop
    }
}

