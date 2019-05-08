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

package playground;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.apache.camel.component.soroushbot.utils.ExponentialBackOffStrategy;

public class OkHTTP {
    public static void main(String[] args) throws InterruptedException {
        String url = "https://bot.sapp.ir/NQcJlsDAviBNaUWPQpLZS3UkUbfiY316QpULBvpZze1aJ4r_RkhQaV9pC0IqRfOl4rBG2TbJc1n2jD4zsIrSirDcb1x_i5wsRQFsNE6s_tS7YMX2kyIYycLI2g6mXbyXB4YBaD-dSWSnPUxV/getMessage";
        Request request = new Request.Builder()
                .url(url)
                .build();
        System.out.println(url);
        OkHttpClient client = new OkHttpClient.Builder().writeTimeout(Duration.ZERO).readTimeout(Duration.ZERO).connectTimeout(10,TimeUnit.SECONDS).callTimeout(Duration.ZERO).build();
        ExponentialBackOffStrategy backOffStrategy = new ExponentialBackOffStrategy(1000l,2l,3600_000L);
        ReconnectableEventSourceListener connection = new ReconnectableEventSourceListener(client, request, 10){
            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                System.out.println(data);
            }

            @Override
            protected boolean onBeforeConnect() {
                try {
                    backOffStrategy.waitBeforeRetry(connectionRetry);
                    return true;
                } catch (InterruptedException e) {
                    return false;
                }
            }
        };
        connection.connect();


    }
    static class ReconnectableEventSourceListener extends EventSourceListener {
        OkHttpClient client;
        final int maxConnectionRetry;
        int connectionRetry=0;
        Request request;
        private final EventSource.Factory factory;

        public ReconnectableEventSourceListener(OkHttpClient client,Request request ,int maxConnectionRetry) {
            this.client = client;
            this.maxConnectionRetry = maxConnectionRetry;
            this.request=request;
            factory = EventSources.createFactory(client);
        }
        public void connect(){
            if(!onBeforeConnect()){
                return;
            }
            if(maxConnectionRetry>=connectionRetry || maxConnectionRetry<0){
                System.out.println(connectionRetry);
                factory.newEventSource(request,this);
            }
        }

        protected boolean onBeforeConnect() {
            return true;
        }

        @Override
        public void onOpen(EventSource eventSource, Response response) {
            System.out.println(Thread.currentThread().toString());
            System.out.println("OPEN");
            connectionRetry=0;
        }

        @Override
        public void onClosed(EventSource eventSource) {
            System.out.println("CLOSED");
            connectionRetry++;
            connect();
        }

        @Override
        public void onFailure(EventSource eventSource, Throwable t, Response response) {
            System.out.println(Thread.currentThread().toString());
            eventSource.cancel();
            System.out.println("FAILURE      "+eventSource.toString());
            connectionRetry++;
            connect();
        }
    }
}
