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

import java.sql.SQLException;

import com.impossibl.postgres.api.jdbc.PGConnection;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultAsyncProducer;

/**
 * The PgEvent producer.
 */
public class PgEventProducer extends DefaultAsyncProducer {
    private final PgEventEndpoint endpoint;
    private PGConnection dbConnection;

    public PgEventProducer(PgEventEndpoint endpoint) throws Exception {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        try {
            if (dbConnection.isClosed()) {
                dbConnection = endpoint.initJdbc();
            }
        } catch (Exception e) {
            exchange.setException(new InvalidStateException("Database connection closed and could not be re-opened.", e));
            callback.done(true);
            return true;
        }

        try {
            dbConnection.createStatement().execute("NOTIFY " + endpoint.getChannel() + ", '" + exchange.getIn().getBody(String.class) + "'");
        } catch (SQLException e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
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
