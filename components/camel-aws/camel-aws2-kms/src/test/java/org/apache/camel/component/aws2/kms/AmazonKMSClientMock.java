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
package org.apache.camel.component.aws2.kms;

import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyResponse;
import software.amazon.awssdk.services.kms.model.EnableKeyRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyResponse;
import software.amazon.awssdk.services.kms.model.KeyListEntry;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.ListKeysRequest;
import software.amazon.awssdk.services.kms.model.ListKeysResponse;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionResponse;

public class AmazonKMSClientMock implements KmsClient {

    public AmazonKMSClientMock() {
    }

    @Override
    public CreateKeyResponse createKey(CreateKeyRequest createKeyRequest) {
        CreateKeyResponse.Builder res = CreateKeyResponse.builder();
        KeyMetadata.Builder metadata = KeyMetadata.builder();
        metadata.keyId("test");
        metadata.enabled(true);
        res.keyMetadata(metadata.build());
        return res.build();
    }

    @Override
    public DescribeKeyResponse describeKey(DescribeKeyRequest describeKeyRequest) {
        DescribeKeyResponse.Builder res = DescribeKeyResponse.builder();
        KeyMetadata.Builder metadata = KeyMetadata.builder();
        metadata.enabled(false);
        metadata.description("MyCamelKey");
        metadata.keyId("test");
        res.keyMetadata(metadata.build());
        return res.build();
    }

    @Override
    public DisableKeyResponse disableKey(DisableKeyRequest disableKeyRequest) {
        DisableKeyResponse res = DisableKeyResponse.builder().build();
        return res;
    }

    @Override
    public EnableKeyResponse enableKey(EnableKeyRequest enableKeyRequest) {
        EnableKeyResponse res = EnableKeyResponse.builder().build();
        return res;
    }

    @Override
    public ListKeysResponse listKeys(ListKeysRequest listKeysRequest) {
        ListKeysResponse.Builder result = ListKeysResponse.builder();
        List<KeyListEntry> keyList = new ArrayList<>();
        KeyListEntry.Builder kle = KeyListEntry.builder();
        kle.keyId("keyId");
        keyList.add(kle.build());
        result.keys(keyList);
        return result.build();
    }

    @Override
    public ScheduleKeyDeletionResponse scheduleKeyDeletion(ScheduleKeyDeletionRequest scheduleKeyDeletionRequest) {
        ScheduleKeyDeletionResponse.Builder response = ScheduleKeyDeletionResponse.builder();
        response.keyId("test");
        return response.build();
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
