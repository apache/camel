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
package org.apache.camel.component.aws2.s3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole("aws2-s3")
public class AWS2S3Console extends AbstractDevConsole {

    public AWS2S3Console() {
        super("camel", "aws2-s3", "AWS S3", "AWS S3 Consumer");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        List<Consumer> list = getCamelContext().getRoutes()
                .stream().map(Route::getConsumer)
                .filter(c -> AWS2S3Consumer.class.getName().equals(c.getClass().getName()))
                .collect(Collectors.toList());

        sb.append(String.format("    %s:%s:%s:%s:%s:%s:%s\n", "bucket", "accessKeys", "defaultCredentialsProvider",
                "profileCredentialsProvider", "maxMessages", "moveAfterRead", "deleteAfterRead"));
        for (Consumer c : list) {
            AWS2S3Consumer nc = (AWS2S3Consumer) c;
            AWS2S3Configuration conf = nc.getEndpoint().getConfiguration();
            sb.append(String.format("    %s:%s:%s:%s:%s:%s:%s\n", conf.getBucketName(),
                    (!conf.isUseDefaultCredentialsProvider() && !conf.isUseProfileCredentialsProvider()),
                    conf.isUseDefaultCredentialsProvider(), conf.isUseProfileCredentialsProvider(), nc.getMaxMessagesPerPoll(),
                    conf.isMoveAfterRead(), conf.isDeleteAfterRead()));
        }
        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        List<Consumer> list = getCamelContext().getRoutes()
                .stream().map(Route::getConsumer)
                .filter(c -> AWS2S3Consumer.class.getName().equals(c.getClass().getName()))
                .collect(Collectors.toList());

        List<JsonObject> arr = new ArrayList<>();
        for (Consumer c : list) {
            AWS2S3Consumer nc = (AWS2S3Consumer) c;
            AWS2S3Configuration conf = nc.getEndpoint().getConfiguration();

            JsonObject jo = new JsonObject();
            jo.put("bucket", conf.getBucketName());
            jo.put("accessKeys", !conf.isUseDefaultCredentialsProvider() && !conf.isUseProfileCredentialsProvider());
            jo.put("defaultCredentialsProvider", conf.isUseDefaultCredentialsProvider());
            jo.put("profileCredentialsProvider", conf.isUseProfileCredentialsProvider());
            jo.put("maxMessages", nc.getMaxMessagesPerPoll());
            jo.put("moveAfterRead", conf.isMoveAfterRead());
            jo.put("deleteAfterRead", conf.isDeleteAfterRead());
            arr.add(jo);
        }
        if (!arr.isEmpty()) {
            root.put("consumers", arr);
        }

        return root;
    }
}
