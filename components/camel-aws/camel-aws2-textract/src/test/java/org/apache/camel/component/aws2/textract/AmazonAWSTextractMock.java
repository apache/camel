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

package org.apache.camel.component.aws2.textract;

import java.util.Collections;

import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.TextractServiceClientConfiguration;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;

public class AmazonAWSTextractMock implements TextractClient {

    @Override
    public DetectDocumentTextResponse detectDocumentText(DetectDocumentTextRequest request) {
        Block block = Block.builder()
                .blockType(BlockType.LINE)
                .text("Mock detected text")
                .build();

        DetectDocumentTextResponse result = DetectDocumentTextResponse.builder()
                .blocks(Collections.singletonList(block))
                .build();
        return result;
    }

    @Override
    public TextractServiceClientConfiguration serviceClientConfiguration() {
        return null;
    }

    @Override
    public String serviceName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }
}
