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
package org.apache.camel.component.pgevent;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;

import com.impossibl.postgres.api.jdbc.PGConnection;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

/**
 * The PgEvent producer.
 */
public class PgEventProducer extends DefaultProducer {
    private final PgEventEndpoint endpoint;
    private PGConnection dbConnection;

    public PgEventProducer(PgEventEndpoint endpoint) throws Exception {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            if (dbConnection.isClosed()) {
                dbConnection = endpoint.initJdbc();
            }
        } catch (Exception e) {
            throw new InvalidStateException("Database connection closed and could not be re-opened.", e);
        }

        String payload = exchange.getIn().getBody(String.class);
        if (dbConnection.isServerMinimumVersion(9, 0)) {
            try (CallableStatement statement = dbConnection.prepareCall("{call pg_notify(?, ?)}")) {
                statement.setString(1, endpoint.getChannel());
                statement.setString(2, payload);
                statement.execute();
            }
        } else {
            String sql = String.format("NOTIFY %s, '%s'", endpoint.getChannel(), payload);
            try (PreparedStatement statement = dbConnection.prepareStatement(sql)) {
                statement.execute();
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        dbConnection = endpoint.initJdbc();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (dbConnection != null) {
            dbConnection.close();
        }
    }

}
