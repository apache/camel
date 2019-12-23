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
package org.apache.camel.component.aws.kms;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.ResponseMetadata;
import com.amazonaws.regions.Region;
import com.amazonaws.services.kms.AbstractAWSKMS;
import com.amazonaws.services.kms.model.CancelKeyDeletionRequest;
import com.amazonaws.services.kms.model.CancelKeyDeletionResult;
import com.amazonaws.services.kms.model.CreateAliasRequest;
import com.amazonaws.services.kms.model.CreateAliasResult;
import com.amazonaws.services.kms.model.CreateGrantRequest;
import com.amazonaws.services.kms.model.CreateGrantResult;
import com.amazonaws.services.kms.model.CreateKeyRequest;
import com.amazonaws.services.kms.model.CreateKeyResult;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.services.kms.model.DecryptResult;
import com.amazonaws.services.kms.model.DeleteAliasRequest;
import com.amazonaws.services.kms.model.DeleteAliasResult;
import com.amazonaws.services.kms.model.DeleteImportedKeyMaterialRequest;
import com.amazonaws.services.kms.model.DeleteImportedKeyMaterialResult;
import com.amazonaws.services.kms.model.DescribeKeyRequest;
import com.amazonaws.services.kms.model.DescribeKeyResult;
import com.amazonaws.services.kms.model.DisableKeyRequest;
import com.amazonaws.services.kms.model.DisableKeyResult;
import com.amazonaws.services.kms.model.DisableKeyRotationRequest;
import com.amazonaws.services.kms.model.DisableKeyRotationResult;
import com.amazonaws.services.kms.model.EnableKeyRequest;
import com.amazonaws.services.kms.model.EnableKeyResult;
import com.amazonaws.services.kms.model.EnableKeyRotationRequest;
import com.amazonaws.services.kms.model.EnableKeyRotationResult;
import com.amazonaws.services.kms.model.EncryptRequest;
import com.amazonaws.services.kms.model.EncryptResult;
import com.amazonaws.services.kms.model.GenerateDataKeyRequest;
import com.amazonaws.services.kms.model.GenerateDataKeyResult;
import com.amazonaws.services.kms.model.GenerateDataKeyWithoutPlaintextRequest;
import com.amazonaws.services.kms.model.GenerateDataKeyWithoutPlaintextResult;
import com.amazonaws.services.kms.model.GenerateRandomRequest;
import com.amazonaws.services.kms.model.GenerateRandomResult;
import com.amazonaws.services.kms.model.GetKeyPolicyRequest;
import com.amazonaws.services.kms.model.GetKeyPolicyResult;
import com.amazonaws.services.kms.model.GetKeyRotationStatusRequest;
import com.amazonaws.services.kms.model.GetKeyRotationStatusResult;
import com.amazonaws.services.kms.model.GetParametersForImportRequest;
import com.amazonaws.services.kms.model.GetParametersForImportResult;
import com.amazonaws.services.kms.model.ImportKeyMaterialRequest;
import com.amazonaws.services.kms.model.ImportKeyMaterialResult;
import com.amazonaws.services.kms.model.KeyListEntry;
import com.amazonaws.services.kms.model.KeyMetadata;
import com.amazonaws.services.kms.model.ListAliasesRequest;
import com.amazonaws.services.kms.model.ListAliasesResult;
import com.amazonaws.services.kms.model.ListGrantsRequest;
import com.amazonaws.services.kms.model.ListGrantsResult;
import com.amazonaws.services.kms.model.ListKeyPoliciesRequest;
import com.amazonaws.services.kms.model.ListKeyPoliciesResult;
import com.amazonaws.services.kms.model.ListKeysRequest;
import com.amazonaws.services.kms.model.ListKeysResult;
import com.amazonaws.services.kms.model.ListResourceTagsRequest;
import com.amazonaws.services.kms.model.ListResourceTagsResult;
import com.amazonaws.services.kms.model.ListRetirableGrantsRequest;
import com.amazonaws.services.kms.model.ListRetirableGrantsResult;
import com.amazonaws.services.kms.model.PutKeyPolicyRequest;
import com.amazonaws.services.kms.model.PutKeyPolicyResult;
import com.amazonaws.services.kms.model.ReEncryptRequest;
import com.amazonaws.services.kms.model.ReEncryptResult;
import com.amazonaws.services.kms.model.RetireGrantRequest;
import com.amazonaws.services.kms.model.RetireGrantResult;
import com.amazonaws.services.kms.model.RevokeGrantRequest;
import com.amazonaws.services.kms.model.RevokeGrantResult;
import com.amazonaws.services.kms.model.ScheduleKeyDeletionRequest;
import com.amazonaws.services.kms.model.ScheduleKeyDeletionResult;
import com.amazonaws.services.kms.model.TagResourceRequest;
import com.amazonaws.services.kms.model.TagResourceResult;
import com.amazonaws.services.kms.model.UntagResourceRequest;
import com.amazonaws.services.kms.model.UntagResourceResult;
import com.amazonaws.services.kms.model.UpdateAliasRequest;
import com.amazonaws.services.kms.model.UpdateAliasResult;
import com.amazonaws.services.kms.model.UpdateKeyDescriptionRequest;
import com.amazonaws.services.kms.model.UpdateKeyDescriptionResult;

public class AmazonKMSClientMock extends AbstractAWSKMS {

    public AmazonKMSClientMock() {
    }

    @Override
    public void setEndpoint(String endpoint) {
    }

    @Override
    public void setRegion(Region region) {
    }

    @Override
    public CancelKeyDeletionResult cancelKeyDeletion(CancelKeyDeletionRequest cancelKeyDeletionRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateAliasResult createAlias(CreateAliasRequest createAliasRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateGrantResult createGrant(CreateGrantRequest createGrantRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateKeyResult createKey(CreateKeyRequest createKeyRequest) {
        CreateKeyResult res = new CreateKeyResult();
        KeyMetadata metadata = new KeyMetadata();
        metadata.setKeyId("test");
        metadata.setEnabled(true);
        res.setKeyMetadata(metadata);
        return res;
    }

    @Override
    public CreateKeyResult createKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DecryptResult decrypt(DecryptRequest decryptRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteAliasResult deleteAlias(DeleteAliasRequest deleteAliasRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteImportedKeyMaterialResult deleteImportedKeyMaterial(DeleteImportedKeyMaterialRequest deleteImportedKeyMaterialRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DescribeKeyResult describeKey(DescribeKeyRequest describeKeyRequest) {
        DescribeKeyResult res = new DescribeKeyResult();
        KeyMetadata metadata = new KeyMetadata();
        metadata.setEnabled(false);
        metadata.setDescription("MyCamelKey");
        metadata.setKeyId("test");
        res.setKeyMetadata(metadata);
        return res;
    }

    @Override
    public DisableKeyResult disableKey(DisableKeyRequest disableKeyRequest) {
        DisableKeyResult res = new DisableKeyResult();
        return res;
    }

    @Override
    public DisableKeyRotationResult disableKeyRotation(DisableKeyRotationRequest disableKeyRotationRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EnableKeyResult enableKey(EnableKeyRequest enableKeyRequest) {
        EnableKeyResult res = new EnableKeyResult();
        return res;
    }

    @Override
    public EnableKeyRotationResult enableKeyRotation(EnableKeyRotationRequest enableKeyRotationRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EncryptResult encrypt(EncryptRequest encryptRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GenerateDataKeyResult generateDataKey(GenerateDataKeyRequest generateDataKeyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GenerateDataKeyWithoutPlaintextResult generateDataKeyWithoutPlaintext(GenerateDataKeyWithoutPlaintextRequest generateDataKeyWithoutPlaintextRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GenerateRandomResult generateRandom(GenerateRandomRequest generateRandomRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GenerateRandomResult generateRandom() {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetKeyPolicyResult getKeyPolicy(GetKeyPolicyRequest getKeyPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetKeyRotationStatusResult getKeyRotationStatus(GetKeyRotationStatusRequest getKeyRotationStatusRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetParametersForImportResult getParametersForImport(GetParametersForImportRequest getParametersForImportRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImportKeyMaterialResult importKeyMaterial(ImportKeyMaterialRequest importKeyMaterialRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListAliasesResult listAliases(ListAliasesRequest listAliasesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListAliasesResult listAliases() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListGrantsResult listGrants(ListGrantsRequest listGrantsRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListKeyPoliciesResult listKeyPolicies(ListKeyPoliciesRequest listKeyPoliciesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListKeysResult listKeys(ListKeysRequest listKeysRequest) {
        ListKeysResult result = new ListKeysResult();
        List<KeyListEntry> keyList = new ArrayList<>();
        KeyListEntry kle = new KeyListEntry();
        kle.withKeyId("keyId");
        keyList.add(kle);
        result.setKeys(keyList);
        return result;
    }

    @Override
    public ListKeysResult listKeys() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListResourceTagsResult listResourceTags(ListResourceTagsRequest listResourceTagsRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListRetirableGrantsResult listRetirableGrants(ListRetirableGrantsRequest listRetirableGrantsRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PutKeyPolicyResult putKeyPolicy(PutKeyPolicyRequest putKeyPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReEncryptResult reEncrypt(ReEncryptRequest reEncryptRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RetireGrantResult retireGrant(RetireGrantRequest retireGrantRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RetireGrantResult retireGrant() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RevokeGrantResult revokeGrant(RevokeGrantRequest revokeGrantRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduleKeyDeletionResult scheduleKeyDeletion(ScheduleKeyDeletionRequest scheduleKeyDeletionRequest) {
        ScheduleKeyDeletionResult result = new ScheduleKeyDeletionResult();
        result.withKeyId("test");
        return result;
    }

    @Override
    public TagResourceResult tagResource(TagResourceRequest tagResourceRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UntagResourceResult untagResource(UntagResourceRequest untagResourceRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateAliasResult updateAlias(UpdateAliasRequest updateAliasRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateKeyDescriptionResult updateKeyDescription(UpdateKeyDescriptionRequest updateKeyDescriptionRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResponseMetadata getCachedResponseMetadata(AmazonWebServiceRequest request) {
        throw new UnsupportedOperationException();
    }

}
