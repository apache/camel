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

import java.sql.PreparedStatement;

import com.impossibl.postgres.api.jdbc.PGConnection;
import com.impossibl.postgres.api.jdbc.PGNotificationListener;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The PgEvent consumer.
 */
public class PgEventConsumer extends DefaultConsumer implements PGNotificationListener {
    private static final Logger LOG = LoggerFactory.getLogger(PgEventConsumer.class);
    private final PgEventEndpoint endpoint;
    private PGConnection dbConnection;

    public PgEventConsumer(PgEventEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        dbConnection = endpoint.initJdbc();
        String sql = String.format("LISTEN %s", endpoint.getChannel());
        try (PreparedStatement statement = dbConnection.prepareStatement(sql)) {
            statement.execute();
        }
        dbConnection.addNotificationListener(endpoint.getChannel(), endpoint.getChannel(), this);
    }


    public void notification(int processId, String channel, String payload) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Notification processId: {}, channel: {}, payload: {}", new Object[]{processId, channel, payload});
        }

        Exchange exchange = endpoint.createExchange();
        Message msg = exchange.getIn();
        msg.setHeader("channel", channel);
        msg.setBody(payload);

        try {
            getProcessor().process(exchange);
        } catch (Exception ex) {
            String cause = "Unable to process incoming notification from PostgreSQL: processId='" + processId + "', channel='" + channel + "', payload='" + payload + "'";
            getExceptionHandler().handleException(cause, ex);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (dbConnection != null) {
            dbConnection.removeNotificationListener(endpoint.getChannel());
            String sql = String.format("UNLISTEN %s", endpoint.getChannel());
            try (PreparedStatement statement = dbConnection.prepareStatement(sql)) {
                statement.execute();
            }
            dbConnection.close();
        }
    }
}
