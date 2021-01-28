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
package org.apache.camel.component.huaweicloud.smn;

import com.huaweicloud.sdk.core.HcClient;
import com.huaweicloud.sdk.smn.v2.SmnClient;
import com.huaweicloud.sdk.smn.v2.model.PublishMessageRequest;
import com.huaweicloud.sdk.smn.v2.model.PublishMessageResponse;

public class SmnClientMock extends SmnClient {
    public SmnClientMock(HcClient hcClient) {
        super(null);
    }

    @Override
    public PublishMessageResponse publishMessage(PublishMessageRequest request) {
        PublishMessageResponse response = new PublishMessageResponse()
                .withRequestId("6a63a18b8bab40ffb71ebd9cb80d0085")
                .withMessageId("bf94b63a5dfb475994d3ac34664e24f2");
        return response;
    }
}
