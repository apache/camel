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

package org.apache.camel.component.aws2.s3.datatype.converter;

import java.util.Map;

import org.apache.camel.Message;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.support.CloudEventsHelper;

/**
 * Output data type represents AWS S3 get object response as CloudEvent V1. The data type sets Camel specific CloudEvent
 * headers on the exchange.
 */
@DataTypeTransformer(name = "aws2-s3:application-cloudevents")
public class AWS2S3CloudEventOutputType extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        final Map<String, Object> headers = message.getHeaders();

        headers.put(CloudEventsHelper.CAMEL_CLOUD_EVENT_ID, message.getExchange().getExchangeId());
        headers.put(CloudEventsHelper.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event.aws.s3.getObject");
        headers.put(CloudEventsHelper.CAMEL_CLOUD_EVENT_SOURCE,
                "aws.s3.bucket." + message.getHeader(AWS2S3Constants.BUCKET_NAME, String.class));
        headers.put(CloudEventsHelper.CAMEL_CLOUD_EVENT_SUBJECT, message.getHeader(AWS2S3Constants.KEY, String.class));
        headers.put(CloudEventsHelper.CAMEL_CLOUD_EVENT_TIME, CloudEventsHelper.getEventTime(message.getExchange()));
    }
}
