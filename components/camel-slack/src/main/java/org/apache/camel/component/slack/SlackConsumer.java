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
package org.apache.camel.component.slack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.slack.utils.SlackUtils.readResponse;

public class SlackConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SlackConsumer.class);

    private SlackEndpoint slackEndpoint;
    private String timestamp;
    private String channelId;

    public SlackConsumer(SlackEndpoint endpoint, Processor processor) throws IOException, ParseException {
        super(endpoint, processor);
        this.slackEndpoint = endpoint;
        this.channelId = getChannelId(slackEndpoint.getChannel());
    }

    @Override
    protected int poll() throws Exception {
        Queue<Exchange> exchanges;

        HttpClient client = HttpClientBuilder.create().useSystemProperties().build();
        HttpPost httpPost = new HttpPost(slackEndpoint.getServerUrl() + "/api/channels.history");
        List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
        params.add(new BasicNameValuePair("channel", channelId));
        if (ObjectHelper.isNotEmpty(timestamp)) {
            params.add(new BasicNameValuePair("oldest", timestamp));
        }
        params.add(new BasicNameValuePair("count", slackEndpoint.getMaxResults()));
        params.add(new BasicNameValuePair("token", slackEndpoint.getToken()));
        httpPost.setEntity(new UrlEncodedFormEntity(params));

        HttpResponse response = client.execute(httpPost);

        String jsonString = readResponse(response);
        JSONParser parser = new JSONParser();

        JSONObject c = (JSONObject)parser.parse(jsonString);
        List list = (List)c.get("messages");
        exchanges = createExchanges(list);
        return processBatch(CastUtils.cast(exchanges));
    }

    private Queue<Exchange> createExchanges(List list) {
        Queue<Exchange> answer = new LinkedList<>();
        if (ObjectHelper.isNotEmpty(list)) {
            Iterator it = list.iterator();
            int i = 0;
            while (it.hasNext()) {
                Object object = (Object)it.next();
                JSONObject singleMess = (JSONObject)object;
                if (i == 0) {
                    timestamp = (String)singleMess.get("ts");
                }
                i++;
                Exchange exchange = slackEndpoint.createExchange(singleMess);
                answer.add(exchange);
            }
        }
        return answer;
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            getAsyncProcessor().process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    LOG.trace("Processing exchange done");
                }
            });
        }

        return total;
    }

    private String getChannelId(String channel) throws IOException, ParseException {
        HttpClient client = HttpClientBuilder.create().useSystemProperties().build();
        HttpPost httpPost = new HttpPost(slackEndpoint.getServerUrl() + "/api/channels.list");

        List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
        params.add(new BasicNameValuePair("token", slackEndpoint.getToken()));
        httpPost.setEntity(new UrlEncodedFormEntity(params));

        HttpResponse response = client.execute(httpPost);

        String jsonString = readResponse(response);
        JSONParser parser = new JSONParser();

        JSONObject c = (JSONObject)parser.parse(jsonString);
        List list = (List)c.get("channels");
        Iterator it = list.iterator();
        int i = 0;
        while (it.hasNext()) {
            Object object = (Object)it.next();
            JSONObject singleChannel = (JSONObject)object;
            if (singleChannel.get("name") != null) {
                if (singleChannel.get("name").equals(channel)) {
                    if (singleChannel.get("id") != null) {
                        return (String)singleChannel.get("id");
                    }
                }
            }

        }
        return jsonString;
    }

}
