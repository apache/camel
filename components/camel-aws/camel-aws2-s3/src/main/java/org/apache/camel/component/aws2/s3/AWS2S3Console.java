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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "aws2-s3", displayName = "AWS S3", description = "AWS S3 Consumer")
public class AWS2S3Console extends AbstractDevConsole {

    public record ConsumerEntry(
            @Metadata(description = "The S3 bucket name") String bucket,
            @Metadata(description = "Whether static access keys are used") boolean accessKeys,
            @Metadata(description = "Whether the default credentials provider is used") boolean defaultCredentialsProvider,
            @Metadata(description = "Whether the profile credentials provider is used") boolean profileCredentialsProvider,
            @Metadata(description = "Maximum number of messages per poll") int maxMessages,
            @Metadata(description = "Whether the object is moved after being read") boolean moveAfterRead,
            @Metadata(description = "Whether the object is deleted after being read") boolean deleteAfterRead) {
    }

    public record Response(
            @Metadata(description = "The AWS S3 consumers (only present when there are any)") List<ConsumerEntry> consumers) {
    }

    public AWS2S3Console() {
        super("camel", "aws2-s3", "AWS S3", "AWS S3 Consumer");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        List<Consumer> list = getCamelContext().getRoutes()
                .stream().map(Route::getConsumer)
                .filter(c -> c instanceof AWS2S3Consumer)
                .collect(Collectors.toList());

        sb.append(String.format("    %s:%s:%s:%s:%s:%s:%s%n", "bucket", "accessKeys", "defaultCredentialsProvider",
                "profileCredentialsProvider", "maxMessages", "moveAfterRead", "deleteAfterRead"));
        for (Consumer c : list) {
            AWS2S3Consumer nc = (AWS2S3Consumer) c;
            AWS2S3Configuration conf = nc.getEndpoint().getConfiguration();
            sb.append(String.format("    %s:%s:%s:%s:%s:%s:%s%n", conf.getBucketName(),
                    (!conf.isUseDefaultCredentialsProvider() && !conf.isUseProfileCredentialsProvider()),
                    conf.isUseDefaultCredentialsProvider(), conf.isUseProfileCredentialsProvider(), nc.getMaxMessagesPerPoll(),
                    conf.isMoveAfterRead(), conf.isDeleteAfterRead()));
        }
        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        List<Consumer> list = getCamelContext().getRoutes()
                .stream().map(Route::getConsumer)
                .filter(c -> c instanceof AWS2S3Consumer)
                .collect(Collectors.toList());

        List<ConsumerEntry> arr = new ArrayList<>();
        for (Consumer c : list) {
            AWS2S3Consumer nc = (AWS2S3Consumer) c;
            AWS2S3Configuration conf = nc.getEndpoint().getConfiguration();

            arr.add(new ConsumerEntry(
                    conf.getBucketName(),
                    !conf.isUseDefaultCredentialsProvider() && !conf.isUseProfileCredentialsProvider(),
                    conf.isUseDefaultCredentialsProvider(), conf.isUseProfileCredentialsProvider(),
                    nc.getMaxMessagesPerPoll(), conf.isMoveAfterRead(), conf.isDeleteAfterRead()));
        }

        Response response = new Response(arr.isEmpty() ? null : arr);
        return JsonRecordSupport.toJsonObject(response);
    }
}
