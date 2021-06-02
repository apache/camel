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
package org.apache.camel;

import com.huaweicloud.sdk.core.HcClient;
import com.huaweicloud.sdk.functiongraph.v2.FunctionGraphClient;
import com.huaweicloud.sdk.functiongraph.v2.model.InvokeFunctionRequest;
import com.huaweicloud.sdk.functiongraph.v2.model.InvokeFunctionResponse;

public class FunctionGraphMockClient extends FunctionGraphClient {
    public FunctionGraphMockClient(HcClient hcClient) {
        super(null);
    }

    @Override
    public InvokeFunctionResponse invokeFunction(InvokeFunctionRequest request) {
        InvokeFunctionResponse response = new InvokeFunctionResponse()
                .withRequestId("1939bbbb-4009-4685-bcc0-2ff0381fa911")
                .withResult(
                        "{\"headers\":{\"Content-Type\":\"application/json\"},\"statusCode\":200,\"isBase64Encoded\":false,\"body\":{\"orderId\":1621950031517,"
                            +
                            "\"department\":\"sales\",\"vendor\":\"huawei\",\"product\":\"monitors\",\"price\":20.13,\"quantity\":20,\"status\":\"order submitted successfully\"}}\n")
                .withStatus(200);
        if (request.getXCffLogType().equals("tail")) {
            response.withLog(
                    "2021-05-25 21:40:31.472+08:00 Start invoke request '1939bbbb-4009-4685-bcc0-2ff0381fa911', version: latest\n"
                             +
                             "    { product: 'monitors',\n" +
                             "      quantity: 20,\n" +
                             "      vendor: 'huawei',\n" +
                             "      price: 20.13,\n" +
                             "      department: 'sales' }\n" +
                             "    2021-05-25 21:40:31.518+08:00 Finish invoke request '1939bbbb-4009-4685-bcc0-2ff0381fa911', duration: 45.204ms, billing duration: 100ms, memory used: 64.383MB.");
        }
        return response;
    }
}
