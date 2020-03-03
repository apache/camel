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
package org.apache.camel.component.aws.iam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.ResponseMetadata;
import com.amazonaws.regions.Region;
import com.amazonaws.services.identitymanagement.AbstractAmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.AccessKey;
import com.amazonaws.services.identitymanagement.model.AccessKeyMetadata;
import com.amazonaws.services.identitymanagement.model.AddClientIDToOpenIDConnectProviderRequest;
import com.amazonaws.services.identitymanagement.model.AddClientIDToOpenIDConnectProviderResult;
import com.amazonaws.services.identitymanagement.model.AddRoleToInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.AddRoleToInstanceProfileResult;
import com.amazonaws.services.identitymanagement.model.AddUserToGroupRequest;
import com.amazonaws.services.identitymanagement.model.AddUserToGroupResult;
import com.amazonaws.services.identitymanagement.model.AttachGroupPolicyRequest;
import com.amazonaws.services.identitymanagement.model.AttachGroupPolicyResult;
import com.amazonaws.services.identitymanagement.model.AttachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.AttachRolePolicyResult;
import com.amazonaws.services.identitymanagement.model.AttachUserPolicyRequest;
import com.amazonaws.services.identitymanagement.model.AttachUserPolicyResult;
import com.amazonaws.services.identitymanagement.model.ChangePasswordRequest;
import com.amazonaws.services.identitymanagement.model.ChangePasswordResult;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyResult;
import com.amazonaws.services.identitymanagement.model.CreateAccountAliasRequest;
import com.amazonaws.services.identitymanagement.model.CreateAccountAliasResult;
import com.amazonaws.services.identitymanagement.model.CreateGroupRequest;
import com.amazonaws.services.identitymanagement.model.CreateGroupResult;
import com.amazonaws.services.identitymanagement.model.CreateInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.CreateInstanceProfileResult;
import com.amazonaws.services.identitymanagement.model.CreateLoginProfileRequest;
import com.amazonaws.services.identitymanagement.model.CreateLoginProfileResult;
import com.amazonaws.services.identitymanagement.model.CreateOpenIDConnectProviderRequest;
import com.amazonaws.services.identitymanagement.model.CreateOpenIDConnectProviderResult;
import com.amazonaws.services.identitymanagement.model.CreatePolicyRequest;
import com.amazonaws.services.identitymanagement.model.CreatePolicyResult;
import com.amazonaws.services.identitymanagement.model.CreatePolicyVersionRequest;
import com.amazonaws.services.identitymanagement.model.CreatePolicyVersionResult;
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest;
import com.amazonaws.services.identitymanagement.model.CreateRoleResult;
import com.amazonaws.services.identitymanagement.model.CreateSAMLProviderRequest;
import com.amazonaws.services.identitymanagement.model.CreateSAMLProviderResult;
import com.amazonaws.services.identitymanagement.model.CreateServiceLinkedRoleRequest;
import com.amazonaws.services.identitymanagement.model.CreateServiceLinkedRoleResult;
import com.amazonaws.services.identitymanagement.model.CreateServiceSpecificCredentialRequest;
import com.amazonaws.services.identitymanagement.model.CreateServiceSpecificCredentialResult;
import com.amazonaws.services.identitymanagement.model.CreateUserRequest;
import com.amazonaws.services.identitymanagement.model.CreateUserResult;
import com.amazonaws.services.identitymanagement.model.CreateVirtualMFADeviceRequest;
import com.amazonaws.services.identitymanagement.model.CreateVirtualMFADeviceResult;
import com.amazonaws.services.identitymanagement.model.DeactivateMFADeviceRequest;
import com.amazonaws.services.identitymanagement.model.DeactivateMFADeviceResult;
import com.amazonaws.services.identitymanagement.model.DeleteAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.DeleteAccessKeyResult;
import com.amazonaws.services.identitymanagement.model.DeleteAccountAliasRequest;
import com.amazonaws.services.identitymanagement.model.DeleteAccountAliasResult;
import com.amazonaws.services.identitymanagement.model.DeleteAccountPasswordPolicyRequest;
import com.amazonaws.services.identitymanagement.model.DeleteAccountPasswordPolicyResult;
import com.amazonaws.services.identitymanagement.model.DeleteGroupPolicyRequest;
import com.amazonaws.services.identitymanagement.model.DeleteGroupPolicyResult;
import com.amazonaws.services.identitymanagement.model.DeleteGroupRequest;
import com.amazonaws.services.identitymanagement.model.DeleteGroupResult;
import com.amazonaws.services.identitymanagement.model.DeleteInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.DeleteInstanceProfileResult;
import com.amazonaws.services.identitymanagement.model.DeleteLoginProfileRequest;
import com.amazonaws.services.identitymanagement.model.DeleteLoginProfileResult;
import com.amazonaws.services.identitymanagement.model.DeleteOpenIDConnectProviderRequest;
import com.amazonaws.services.identitymanagement.model.DeleteOpenIDConnectProviderResult;
import com.amazonaws.services.identitymanagement.model.DeletePolicyRequest;
import com.amazonaws.services.identitymanagement.model.DeletePolicyResult;
import com.amazonaws.services.identitymanagement.model.DeletePolicyVersionRequest;
import com.amazonaws.services.identitymanagement.model.DeletePolicyVersionResult;
import com.amazonaws.services.identitymanagement.model.DeleteRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.DeleteRolePolicyResult;
import com.amazonaws.services.identitymanagement.model.DeleteRoleRequest;
import com.amazonaws.services.identitymanagement.model.DeleteRoleResult;
import com.amazonaws.services.identitymanagement.model.DeleteSAMLProviderRequest;
import com.amazonaws.services.identitymanagement.model.DeleteSAMLProviderResult;
import com.amazonaws.services.identitymanagement.model.DeleteSSHPublicKeyRequest;
import com.amazonaws.services.identitymanagement.model.DeleteSSHPublicKeyResult;
import com.amazonaws.services.identitymanagement.model.DeleteServerCertificateRequest;
import com.amazonaws.services.identitymanagement.model.DeleteServerCertificateResult;
import com.amazonaws.services.identitymanagement.model.DeleteServiceLinkedRoleRequest;
import com.amazonaws.services.identitymanagement.model.DeleteServiceLinkedRoleResult;
import com.amazonaws.services.identitymanagement.model.DeleteServiceSpecificCredentialRequest;
import com.amazonaws.services.identitymanagement.model.DeleteServiceSpecificCredentialResult;
import com.amazonaws.services.identitymanagement.model.DeleteSigningCertificateRequest;
import com.amazonaws.services.identitymanagement.model.DeleteSigningCertificateResult;
import com.amazonaws.services.identitymanagement.model.DeleteUserPolicyRequest;
import com.amazonaws.services.identitymanagement.model.DeleteUserPolicyResult;
import com.amazonaws.services.identitymanagement.model.DeleteUserRequest;
import com.amazonaws.services.identitymanagement.model.DeleteUserResult;
import com.amazonaws.services.identitymanagement.model.DeleteVirtualMFADeviceRequest;
import com.amazonaws.services.identitymanagement.model.DeleteVirtualMFADeviceResult;
import com.amazonaws.services.identitymanagement.model.DetachGroupPolicyRequest;
import com.amazonaws.services.identitymanagement.model.DetachGroupPolicyResult;
import com.amazonaws.services.identitymanagement.model.DetachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.DetachRolePolicyResult;
import com.amazonaws.services.identitymanagement.model.DetachUserPolicyRequest;
import com.amazonaws.services.identitymanagement.model.DetachUserPolicyResult;
import com.amazonaws.services.identitymanagement.model.EnableMFADeviceRequest;
import com.amazonaws.services.identitymanagement.model.EnableMFADeviceResult;
import com.amazonaws.services.identitymanagement.model.GenerateCredentialReportRequest;
import com.amazonaws.services.identitymanagement.model.GenerateCredentialReportResult;
import com.amazonaws.services.identitymanagement.model.GetAccessKeyLastUsedRequest;
import com.amazonaws.services.identitymanagement.model.GetAccessKeyLastUsedResult;
import com.amazonaws.services.identitymanagement.model.GetAccountAuthorizationDetailsRequest;
import com.amazonaws.services.identitymanagement.model.GetAccountAuthorizationDetailsResult;
import com.amazonaws.services.identitymanagement.model.GetAccountPasswordPolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetAccountPasswordPolicyResult;
import com.amazonaws.services.identitymanagement.model.GetAccountSummaryRequest;
import com.amazonaws.services.identitymanagement.model.GetAccountSummaryResult;
import com.amazonaws.services.identitymanagement.model.GetContextKeysForCustomPolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetContextKeysForCustomPolicyResult;
import com.amazonaws.services.identitymanagement.model.GetContextKeysForPrincipalPolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetContextKeysForPrincipalPolicyResult;
import com.amazonaws.services.identitymanagement.model.GetCredentialReportRequest;
import com.amazonaws.services.identitymanagement.model.GetCredentialReportResult;
import com.amazonaws.services.identitymanagement.model.GetGroupPolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetGroupPolicyResult;
import com.amazonaws.services.identitymanagement.model.GetGroupRequest;
import com.amazonaws.services.identitymanagement.model.GetGroupResult;
import com.amazonaws.services.identitymanagement.model.GetInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.GetInstanceProfileResult;
import com.amazonaws.services.identitymanagement.model.GetLoginProfileRequest;
import com.amazonaws.services.identitymanagement.model.GetLoginProfileResult;
import com.amazonaws.services.identitymanagement.model.GetOpenIDConnectProviderRequest;
import com.amazonaws.services.identitymanagement.model.GetOpenIDConnectProviderResult;
import com.amazonaws.services.identitymanagement.model.GetPolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetPolicyResult;
import com.amazonaws.services.identitymanagement.model.GetPolicyVersionRequest;
import com.amazonaws.services.identitymanagement.model.GetPolicyVersionResult;
import com.amazonaws.services.identitymanagement.model.GetRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetRolePolicyResult;
import com.amazonaws.services.identitymanagement.model.GetRoleRequest;
import com.amazonaws.services.identitymanagement.model.GetRoleResult;
import com.amazonaws.services.identitymanagement.model.GetSAMLProviderRequest;
import com.amazonaws.services.identitymanagement.model.GetSAMLProviderResult;
import com.amazonaws.services.identitymanagement.model.GetSSHPublicKeyRequest;
import com.amazonaws.services.identitymanagement.model.GetSSHPublicKeyResult;
import com.amazonaws.services.identitymanagement.model.GetServerCertificateRequest;
import com.amazonaws.services.identitymanagement.model.GetServerCertificateResult;
import com.amazonaws.services.identitymanagement.model.GetServiceLinkedRoleDeletionStatusRequest;
import com.amazonaws.services.identitymanagement.model.GetServiceLinkedRoleDeletionStatusResult;
import com.amazonaws.services.identitymanagement.model.GetUserPolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetUserPolicyResult;
import com.amazonaws.services.identitymanagement.model.GetUserRequest;
import com.amazonaws.services.identitymanagement.model.GetUserResult;
import com.amazonaws.services.identitymanagement.model.Group;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysRequest;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysResult;
import com.amazonaws.services.identitymanagement.model.ListAccountAliasesRequest;
import com.amazonaws.services.identitymanagement.model.ListAccountAliasesResult;
import com.amazonaws.services.identitymanagement.model.ListAttachedGroupPoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListAttachedGroupPoliciesResult;
import com.amazonaws.services.identitymanagement.model.ListAttachedRolePoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListAttachedRolePoliciesResult;
import com.amazonaws.services.identitymanagement.model.ListAttachedUserPoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListAttachedUserPoliciesResult;
import com.amazonaws.services.identitymanagement.model.ListEntitiesForPolicyRequest;
import com.amazonaws.services.identitymanagement.model.ListEntitiesForPolicyResult;
import com.amazonaws.services.identitymanagement.model.ListGroupPoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListGroupPoliciesResult;
import com.amazonaws.services.identitymanagement.model.ListGroupsForUserRequest;
import com.amazonaws.services.identitymanagement.model.ListGroupsForUserResult;
import com.amazonaws.services.identitymanagement.model.ListGroupsRequest;
import com.amazonaws.services.identitymanagement.model.ListGroupsResult;
import com.amazonaws.services.identitymanagement.model.ListInstanceProfilesForRoleRequest;
import com.amazonaws.services.identitymanagement.model.ListInstanceProfilesForRoleResult;
import com.amazonaws.services.identitymanagement.model.ListInstanceProfilesRequest;
import com.amazonaws.services.identitymanagement.model.ListInstanceProfilesResult;
import com.amazonaws.services.identitymanagement.model.ListMFADevicesRequest;
import com.amazonaws.services.identitymanagement.model.ListMFADevicesResult;
import com.amazonaws.services.identitymanagement.model.ListOpenIDConnectProvidersRequest;
import com.amazonaws.services.identitymanagement.model.ListOpenIDConnectProvidersResult;
import com.amazonaws.services.identitymanagement.model.ListPoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListPoliciesResult;
import com.amazonaws.services.identitymanagement.model.ListPolicyVersionsRequest;
import com.amazonaws.services.identitymanagement.model.ListPolicyVersionsResult;
import com.amazonaws.services.identitymanagement.model.ListRolePoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolePoliciesResult;
import com.amazonaws.services.identitymanagement.model.ListRolesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.ListSAMLProvidersRequest;
import com.amazonaws.services.identitymanagement.model.ListSAMLProvidersResult;
import com.amazonaws.services.identitymanagement.model.ListSSHPublicKeysRequest;
import com.amazonaws.services.identitymanagement.model.ListSSHPublicKeysResult;
import com.amazonaws.services.identitymanagement.model.ListServerCertificatesRequest;
import com.amazonaws.services.identitymanagement.model.ListServerCertificatesResult;
import com.amazonaws.services.identitymanagement.model.ListServiceSpecificCredentialsRequest;
import com.amazonaws.services.identitymanagement.model.ListServiceSpecificCredentialsResult;
import com.amazonaws.services.identitymanagement.model.ListSigningCertificatesRequest;
import com.amazonaws.services.identitymanagement.model.ListSigningCertificatesResult;
import com.amazonaws.services.identitymanagement.model.ListUserPoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListUserPoliciesResult;
import com.amazonaws.services.identitymanagement.model.ListUsersRequest;
import com.amazonaws.services.identitymanagement.model.ListUsersResult;
import com.amazonaws.services.identitymanagement.model.ListVirtualMFADevicesRequest;
import com.amazonaws.services.identitymanagement.model.ListVirtualMFADevicesResult;
import com.amazonaws.services.identitymanagement.model.PutGroupPolicyRequest;
import com.amazonaws.services.identitymanagement.model.PutGroupPolicyResult;
import com.amazonaws.services.identitymanagement.model.PutRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.PutRolePolicyResult;
import com.amazonaws.services.identitymanagement.model.PutUserPolicyRequest;
import com.amazonaws.services.identitymanagement.model.PutUserPolicyResult;
import com.amazonaws.services.identitymanagement.model.RemoveClientIDFromOpenIDConnectProviderRequest;
import com.amazonaws.services.identitymanagement.model.RemoveClientIDFromOpenIDConnectProviderResult;
import com.amazonaws.services.identitymanagement.model.RemoveRoleFromInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.RemoveRoleFromInstanceProfileResult;
import com.amazonaws.services.identitymanagement.model.RemoveUserFromGroupRequest;
import com.amazonaws.services.identitymanagement.model.RemoveUserFromGroupResult;
import com.amazonaws.services.identitymanagement.model.ResetServiceSpecificCredentialRequest;
import com.amazonaws.services.identitymanagement.model.ResetServiceSpecificCredentialResult;
import com.amazonaws.services.identitymanagement.model.ResyncMFADeviceRequest;
import com.amazonaws.services.identitymanagement.model.ResyncMFADeviceResult;
import com.amazonaws.services.identitymanagement.model.SetDefaultPolicyVersionRequest;
import com.amazonaws.services.identitymanagement.model.SetDefaultPolicyVersionResult;
import com.amazonaws.services.identitymanagement.model.SimulateCustomPolicyRequest;
import com.amazonaws.services.identitymanagement.model.SimulateCustomPolicyResult;
import com.amazonaws.services.identitymanagement.model.SimulatePrincipalPolicyRequest;
import com.amazonaws.services.identitymanagement.model.SimulatePrincipalPolicyResult;
import com.amazonaws.services.identitymanagement.model.StatusType;
import com.amazonaws.services.identitymanagement.model.UpdateAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.UpdateAccessKeyResult;
import com.amazonaws.services.identitymanagement.model.UpdateAccountPasswordPolicyRequest;
import com.amazonaws.services.identitymanagement.model.UpdateAccountPasswordPolicyResult;
import com.amazonaws.services.identitymanagement.model.UpdateAssumeRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.UpdateAssumeRolePolicyResult;
import com.amazonaws.services.identitymanagement.model.UpdateGroupRequest;
import com.amazonaws.services.identitymanagement.model.UpdateGroupResult;
import com.amazonaws.services.identitymanagement.model.UpdateLoginProfileRequest;
import com.amazonaws.services.identitymanagement.model.UpdateLoginProfileResult;
import com.amazonaws.services.identitymanagement.model.UpdateOpenIDConnectProviderThumbprintRequest;
import com.amazonaws.services.identitymanagement.model.UpdateOpenIDConnectProviderThumbprintResult;
import com.amazonaws.services.identitymanagement.model.UpdateRoleDescriptionRequest;
import com.amazonaws.services.identitymanagement.model.UpdateRoleDescriptionResult;
import com.amazonaws.services.identitymanagement.model.UpdateSAMLProviderRequest;
import com.amazonaws.services.identitymanagement.model.UpdateSAMLProviderResult;
import com.amazonaws.services.identitymanagement.model.UpdateSSHPublicKeyRequest;
import com.amazonaws.services.identitymanagement.model.UpdateSSHPublicKeyResult;
import com.amazonaws.services.identitymanagement.model.UpdateServerCertificateRequest;
import com.amazonaws.services.identitymanagement.model.UpdateServerCertificateResult;
import com.amazonaws.services.identitymanagement.model.UpdateServiceSpecificCredentialRequest;
import com.amazonaws.services.identitymanagement.model.UpdateServiceSpecificCredentialResult;
import com.amazonaws.services.identitymanagement.model.UpdateSigningCertificateRequest;
import com.amazonaws.services.identitymanagement.model.UpdateSigningCertificateResult;
import com.amazonaws.services.identitymanagement.model.UpdateUserRequest;
import com.amazonaws.services.identitymanagement.model.UpdateUserResult;
import com.amazonaws.services.identitymanagement.model.UploadSSHPublicKeyRequest;
import com.amazonaws.services.identitymanagement.model.UploadSSHPublicKeyResult;
import com.amazonaws.services.identitymanagement.model.UploadServerCertificateRequest;
import com.amazonaws.services.identitymanagement.model.UploadServerCertificateResult;
import com.amazonaws.services.identitymanagement.model.UploadSigningCertificateRequest;
import com.amazonaws.services.identitymanagement.model.UploadSigningCertificateResult;
import com.amazonaws.services.identitymanagement.model.User;
import com.amazonaws.services.identitymanagement.waiters.AmazonIdentityManagementWaiters;

public class AmazonIAMClientMock extends AbstractAmazonIdentityManagement {

    @Override
    public void setEndpoint(String endpoint) {
    }

    @Override
    public void setRegion(Region region) {
    }

    @Override
    public AddClientIDToOpenIDConnectProviderResult addClientIDToOpenIDConnectProvider(AddClientIDToOpenIDConnectProviderRequest addClientIDToOpenIDConnectProviderRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AddRoleToInstanceProfileResult addRoleToInstanceProfile(AddRoleToInstanceProfileRequest addRoleToInstanceProfileRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AddUserToGroupResult addUserToGroup(AddUserToGroupRequest addUserToGroupRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AttachGroupPolicyResult attachGroupPolicy(AttachGroupPolicyRequest attachGroupPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AttachRolePolicyResult attachRolePolicy(AttachRolePolicyRequest attachRolePolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AttachUserPolicyResult attachUserPolicy(AttachUserPolicyRequest attachUserPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChangePasswordResult changePassword(ChangePasswordRequest changePasswordRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateAccessKeyResult createAccessKey(CreateAccessKeyRequest createAccessKeyRequest) {
        CreateAccessKeyResult res = new CreateAccessKeyResult();
        AccessKey key = new AccessKey();
        key.setAccessKeyId("test");
        key.setSecretAccessKey("testSecret");
        res.setAccessKey(key);
        return res;
    }

    @Override
    public CreateAccessKeyResult createAccessKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateAccountAliasResult createAccountAlias(CreateAccountAliasRequest createAccountAliasRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateGroupResult createGroup(CreateGroupRequest createGroupRequest) {
        CreateGroupResult result = new CreateGroupResult();
        Group group = new Group();
        group.setGroupName(createGroupRequest.getGroupName());
        if (createGroupRequest.getPath() != null) {
            group.setPath(createGroupRequest.getPath());
        }
        group.setGroupId("TestGroup");
        result.setGroup(group);
        return result;
    }

    @Override
    public CreateInstanceProfileResult createInstanceProfile(CreateInstanceProfileRequest createInstanceProfileRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateLoginProfileResult createLoginProfile(CreateLoginProfileRequest createLoginProfileRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateOpenIDConnectProviderResult createOpenIDConnectProvider(CreateOpenIDConnectProviderRequest createOpenIDConnectProviderRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreatePolicyResult createPolicy(CreatePolicyRequest createPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreatePolicyVersionResult createPolicyVersion(CreatePolicyVersionRequest createPolicyVersionRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateRoleResult createRole(CreateRoleRequest createRoleRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateSAMLProviderResult createSAMLProvider(CreateSAMLProviderRequest createSAMLProviderRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateServiceLinkedRoleResult createServiceLinkedRole(CreateServiceLinkedRoleRequest createServiceLinkedRoleRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateServiceSpecificCredentialResult createServiceSpecificCredential(CreateServiceSpecificCredentialRequest createServiceSpecificCredentialRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateUserResult createUser(CreateUserRequest createUserRequest) {
        CreateUserResult result = new CreateUserResult();
        User user = new User();
        user.setUserName("test");
        result.setUser(user);
        return result;
    }

    @Override
    public CreateVirtualMFADeviceResult createVirtualMFADevice(CreateVirtualMFADeviceRequest createVirtualMFADeviceRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeactivateMFADeviceResult deactivateMFADevice(DeactivateMFADeviceRequest deactivateMFADeviceRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteAccessKeyResult deleteAccessKey(DeleteAccessKeyRequest deleteAccessKeyRequest) {
        DeleteAccessKeyResult res = new DeleteAccessKeyResult();
        return res;
    }

    @Override
    public DeleteAccountAliasResult deleteAccountAlias(DeleteAccountAliasRequest deleteAccountAliasRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteAccountPasswordPolicyResult deleteAccountPasswordPolicy(DeleteAccountPasswordPolicyRequest deleteAccountPasswordPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteAccountPasswordPolicyResult deleteAccountPasswordPolicy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteGroupResult deleteGroup(DeleteGroupRequest deleteGroupRequest) {
        DeleteGroupResult result = new DeleteGroupResult();
        return result;
    }

    @Override
    public DeleteGroupPolicyResult deleteGroupPolicy(DeleteGroupPolicyRequest deleteGroupPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteInstanceProfileResult deleteInstanceProfile(DeleteInstanceProfileRequest deleteInstanceProfileRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteLoginProfileResult deleteLoginProfile(DeleteLoginProfileRequest deleteLoginProfileRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteOpenIDConnectProviderResult deleteOpenIDConnectProvider(DeleteOpenIDConnectProviderRequest deleteOpenIDConnectProviderRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeletePolicyResult deletePolicy(DeletePolicyRequest deletePolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeletePolicyVersionResult deletePolicyVersion(DeletePolicyVersionRequest deletePolicyVersionRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteRoleResult deleteRole(DeleteRoleRequest deleteRoleRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteRolePolicyResult deleteRolePolicy(DeleteRolePolicyRequest deleteRolePolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteSAMLProviderResult deleteSAMLProvider(DeleteSAMLProviderRequest deleteSAMLProviderRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteSSHPublicKeyResult deleteSSHPublicKey(DeleteSSHPublicKeyRequest deleteSSHPublicKeyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteServerCertificateResult deleteServerCertificate(DeleteServerCertificateRequest deleteServerCertificateRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteServiceLinkedRoleResult deleteServiceLinkedRole(DeleteServiceLinkedRoleRequest deleteServiceLinkedRoleRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteServiceSpecificCredentialResult deleteServiceSpecificCredential(DeleteServiceSpecificCredentialRequest deleteServiceSpecificCredentialRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteSigningCertificateResult deleteSigningCertificate(DeleteSigningCertificateRequest deleteSigningCertificateRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteUserResult deleteUser(DeleteUserRequest deleteUserRequest) {
        DeleteUserResult res = new DeleteUserResult();
        return res;
    }

    @Override
    public DeleteUserPolicyResult deleteUserPolicy(DeleteUserPolicyRequest deleteUserPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteVirtualMFADeviceResult deleteVirtualMFADevice(DeleteVirtualMFADeviceRequest deleteVirtualMFADeviceRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DetachGroupPolicyResult detachGroupPolicy(DetachGroupPolicyRequest detachGroupPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DetachRolePolicyResult detachRolePolicy(DetachRolePolicyRequest detachRolePolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DetachUserPolicyResult detachUserPolicy(DetachUserPolicyRequest detachUserPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EnableMFADeviceResult enableMFADevice(EnableMFADeviceRequest enableMFADeviceRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GenerateCredentialReportResult generateCredentialReport(GenerateCredentialReportRequest generateCredentialReportRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GenerateCredentialReportResult generateCredentialReport() {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetAccessKeyLastUsedResult getAccessKeyLastUsed(GetAccessKeyLastUsedRequest getAccessKeyLastUsedRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetAccountAuthorizationDetailsResult getAccountAuthorizationDetails(GetAccountAuthorizationDetailsRequest getAccountAuthorizationDetailsRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetAccountAuthorizationDetailsResult getAccountAuthorizationDetails() {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetAccountPasswordPolicyResult getAccountPasswordPolicy(GetAccountPasswordPolicyRequest getAccountPasswordPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetAccountPasswordPolicyResult getAccountPasswordPolicy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetAccountSummaryResult getAccountSummary(GetAccountSummaryRequest getAccountSummaryRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetAccountSummaryResult getAccountSummary() {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetContextKeysForCustomPolicyResult getContextKeysForCustomPolicy(GetContextKeysForCustomPolicyRequest getContextKeysForCustomPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetContextKeysForPrincipalPolicyResult getContextKeysForPrincipalPolicy(GetContextKeysForPrincipalPolicyRequest getContextKeysForPrincipalPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetCredentialReportResult getCredentialReport(GetCredentialReportRequest getCredentialReportRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetCredentialReportResult getCredentialReport() {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetGroupResult getGroup(GetGroupRequest getGroupRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetGroupPolicyResult getGroupPolicy(GetGroupPolicyRequest getGroupPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetInstanceProfileResult getInstanceProfile(GetInstanceProfileRequest getInstanceProfileRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetLoginProfileResult getLoginProfile(GetLoginProfileRequest getLoginProfileRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetOpenIDConnectProviderResult getOpenIDConnectProvider(GetOpenIDConnectProviderRequest getOpenIDConnectProviderRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetPolicyResult getPolicy(GetPolicyRequest getPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetPolicyVersionResult getPolicyVersion(GetPolicyVersionRequest getPolicyVersionRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetRoleResult getRole(GetRoleRequest getRoleRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetRolePolicyResult getRolePolicy(GetRolePolicyRequest getRolePolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetSAMLProviderResult getSAMLProvider(GetSAMLProviderRequest getSAMLProviderRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetSSHPublicKeyResult getSSHPublicKey(GetSSHPublicKeyRequest getSSHPublicKeyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetServerCertificateResult getServerCertificate(GetServerCertificateRequest getServerCertificateRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetServiceLinkedRoleDeletionStatusResult getServiceLinkedRoleDeletionStatus(GetServiceLinkedRoleDeletionStatusRequest getServiceLinkedRoleDeletionStatusRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetUserResult getUser(GetUserRequest getUserRequest) {
        GetUserResult result = new GetUserResult();
        User user = new User();
        user.setUserName("test");
        result.setUser(user);
        return result;
    }

    @Override
    public GetUserResult getUser() {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetUserPolicyResult getUserPolicy(GetUserPolicyRequest getUserPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListAccessKeysResult listAccessKeys(ListAccessKeysRequest listAccessKeysRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListAccessKeysResult listAccessKeys() {
        ListAccessKeysResult result = new ListAccessKeysResult();
        Collection<AccessKeyMetadata> accessKeyMetadata = new ArrayList<>();
        AccessKeyMetadata meta = new AccessKeyMetadata();
        meta.setAccessKeyId("1");
        meta.setCreateDate(new Date());
        meta.setStatus(StatusType.Active);
        meta.setUserName("test");
        accessKeyMetadata.add(meta);
        result.setAccessKeyMetadata(accessKeyMetadata);
        return result;
    }

    @Override
    public ListAccountAliasesResult listAccountAliases(ListAccountAliasesRequest listAccountAliasesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListAccountAliasesResult listAccountAliases() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListAttachedGroupPoliciesResult listAttachedGroupPolicies(ListAttachedGroupPoliciesRequest listAttachedGroupPoliciesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListAttachedRolePoliciesResult listAttachedRolePolicies(ListAttachedRolePoliciesRequest listAttachedRolePoliciesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListAttachedUserPoliciesResult listAttachedUserPolicies(ListAttachedUserPoliciesRequest listAttachedUserPoliciesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListEntitiesForPolicyResult listEntitiesForPolicy(ListEntitiesForPolicyRequest listEntitiesForPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListGroupPoliciesResult listGroupPolicies(ListGroupPoliciesRequest listGroupPoliciesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListGroupsResult listGroups(ListGroupsRequest listGroupsRequest) {
        Group group = new Group();
        group.setGroupId("TestGroup");
        group.setGroupName("Test");
        ListGroupsResult res = new ListGroupsResult();
        res.setGroups(Collections.singleton(group));
        return res;
    }

    @Override
    public ListGroupsResult listGroups() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListGroupsForUserResult listGroupsForUser(ListGroupsForUserRequest listGroupsForUserRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListInstanceProfilesResult listInstanceProfiles(ListInstanceProfilesRequest listInstanceProfilesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListInstanceProfilesResult listInstanceProfiles() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListInstanceProfilesForRoleResult listInstanceProfilesForRole(ListInstanceProfilesForRoleRequest listInstanceProfilesForRoleRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListMFADevicesResult listMFADevices(ListMFADevicesRequest listMFADevicesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListMFADevicesResult listMFADevices() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListOpenIDConnectProvidersResult listOpenIDConnectProviders(ListOpenIDConnectProvidersRequest listOpenIDConnectProvidersRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListOpenIDConnectProvidersResult listOpenIDConnectProviders() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListPoliciesResult listPolicies(ListPoliciesRequest listPoliciesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListPoliciesResult listPolicies() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListPolicyVersionsResult listPolicyVersions(ListPolicyVersionsRequest listPolicyVersionsRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListRolePoliciesResult listRolePolicies(ListRolePoliciesRequest listRolePoliciesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListRolesResult listRoles(ListRolesRequest listRolesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListRolesResult listRoles() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListSAMLProvidersResult listSAMLProviders(ListSAMLProvidersRequest listSAMLProvidersRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListSAMLProvidersResult listSAMLProviders() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListSSHPublicKeysResult listSSHPublicKeys(ListSSHPublicKeysRequest listSSHPublicKeysRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListSSHPublicKeysResult listSSHPublicKeys() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListServerCertificatesResult listServerCertificates(ListServerCertificatesRequest listServerCertificatesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListServerCertificatesResult listServerCertificates() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListServiceSpecificCredentialsResult listServiceSpecificCredentials(ListServiceSpecificCredentialsRequest listServiceSpecificCredentialsRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListSigningCertificatesResult listSigningCertificates(ListSigningCertificatesRequest listSigningCertificatesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListSigningCertificatesResult listSigningCertificates() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListUserPoliciesResult listUserPolicies(ListUserPoliciesRequest listUserPoliciesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListUsersResult listUsers(ListUsersRequest listUsersRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListUsersResult listUsers() {
        ListUsersResult res = new ListUsersResult();
        List<User> list = new ArrayList<>();
        User user = new User();
        user.setUserName("test");
        list.add(user);
        res.setUsers(list);
        return res;
    }

    @Override
    public ListVirtualMFADevicesResult listVirtualMFADevices(ListVirtualMFADevicesRequest listVirtualMFADevicesRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListVirtualMFADevicesResult listVirtualMFADevices() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PutGroupPolicyResult putGroupPolicy(PutGroupPolicyRequest putGroupPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PutRolePolicyResult putRolePolicy(PutRolePolicyRequest putRolePolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PutUserPolicyResult putUserPolicy(PutUserPolicyRequest putUserPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RemoveClientIDFromOpenIDConnectProviderResult removeClientIDFromOpenIDConnectProvider(RemoveClientIDFromOpenIDConnectProviderRequest removeClientIDFromOpenIDConnectProviderRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RemoveRoleFromInstanceProfileResult removeRoleFromInstanceProfile(RemoveRoleFromInstanceProfileRequest removeRoleFromInstanceProfileRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RemoveUserFromGroupResult removeUserFromGroup(RemoveUserFromGroupRequest removeUserFromGroupRequest) {
        RemoveUserFromGroupResult res = new RemoveUserFromGroupResult();
        return res;
    }

    @Override
    public ResetServiceSpecificCredentialResult resetServiceSpecificCredential(ResetServiceSpecificCredentialRequest resetServiceSpecificCredentialRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResyncMFADeviceResult resyncMFADevice(ResyncMFADeviceRequest resyncMFADeviceRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SetDefaultPolicyVersionResult setDefaultPolicyVersion(SetDefaultPolicyVersionRequest setDefaultPolicyVersionRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SimulateCustomPolicyResult simulateCustomPolicy(SimulateCustomPolicyRequest simulateCustomPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SimulatePrincipalPolicyResult simulatePrincipalPolicy(SimulatePrincipalPolicyRequest simulatePrincipalPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateAccessKeyResult updateAccessKey(UpdateAccessKeyRequest updateAccessKeyRequest) {
        UpdateAccessKeyResult result = new UpdateAccessKeyResult();
        return result;
    }

    @Override
    public UpdateAccountPasswordPolicyResult updateAccountPasswordPolicy(UpdateAccountPasswordPolicyRequest updateAccountPasswordPolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateAssumeRolePolicyResult updateAssumeRolePolicy(UpdateAssumeRolePolicyRequest updateAssumeRolePolicyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateGroupResult updateGroup(UpdateGroupRequest updateGroupRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateLoginProfileResult updateLoginProfile(UpdateLoginProfileRequest updateLoginProfileRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateOpenIDConnectProviderThumbprintResult updateOpenIDConnectProviderThumbprint(UpdateOpenIDConnectProviderThumbprintRequest updateOpenIDConnectProviderThumbprintRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateRoleDescriptionResult updateRoleDescription(UpdateRoleDescriptionRequest updateRoleDescriptionRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateSAMLProviderResult updateSAMLProvider(UpdateSAMLProviderRequest updateSAMLProviderRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateSSHPublicKeyResult updateSSHPublicKey(UpdateSSHPublicKeyRequest updateSSHPublicKeyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateServerCertificateResult updateServerCertificate(UpdateServerCertificateRequest updateServerCertificateRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateServiceSpecificCredentialResult updateServiceSpecificCredential(UpdateServiceSpecificCredentialRequest updateServiceSpecificCredentialRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateSigningCertificateResult updateSigningCertificate(UpdateSigningCertificateRequest updateSigningCertificateRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateUserResult updateUser(UpdateUserRequest updateUserRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UploadSSHPublicKeyResult uploadSSHPublicKey(UploadSSHPublicKeyRequest uploadSSHPublicKeyRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UploadServerCertificateResult uploadServerCertificate(UploadServerCertificateRequest uploadServerCertificateRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UploadSigningCertificateResult uploadSigningCertificate(UploadSigningCertificateRequest uploadSigningCertificateRequest) {
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

    @Override
    public AmazonIdentityManagementWaiters waiters() {
        throw new UnsupportedOperationException();
    }
}
