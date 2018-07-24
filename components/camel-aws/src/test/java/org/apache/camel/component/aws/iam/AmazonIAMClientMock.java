/**
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
import java.util.Date;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.ResponseMetadata;
import com.amazonaws.regions.Region;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.*;
import com.amazonaws.services.identitymanagement.waiters.AmazonIdentityManagementWaiters;


public class AmazonIAMClientMock extends AmazonIdentityManagementClient {

	@Override
	public void setEndpoint(String endpoint) {
	}

	@Override
	public void setRegion(Region region) {	
	}

	@Override
	public AddClientIDToOpenIDConnectProviderResult addClientIDToOpenIDConnectProvider(
			AddClientIDToOpenIDConnectProviderRequest addClientIDToOpenIDConnectProviderRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public AddRoleToInstanceProfileResult addRoleToInstanceProfile(
			AddRoleToInstanceProfileRequest addRoleToInstanceProfileRequest) {
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
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
	}

	@Override
	public CreateInstanceProfileResult createInstanceProfile(
			CreateInstanceProfileRequest createInstanceProfileRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public CreateLoginProfileResult createLoginProfile(CreateLoginProfileRequest createLoginProfileRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public CreateOpenIDConnectProviderResult createOpenIDConnectProvider(
			CreateOpenIDConnectProviderRequest createOpenIDConnectProviderRequest) {
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
	public CreateServiceLinkedRoleResult createServiceLinkedRole(
			CreateServiceLinkedRoleRequest createServiceLinkedRoleRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public CreateServiceSpecificCredentialResult createServiceSpecificCredential(
			CreateServiceSpecificCredentialRequest createServiceSpecificCredentialRequest) {
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
	public CreateVirtualMFADeviceResult createVirtualMFADevice(
			CreateVirtualMFADeviceRequest createVirtualMFADeviceRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeactivateMFADeviceResult deactivateMFADevice(DeactivateMFADeviceRequest deactivateMFADeviceRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteAccessKeyResult deleteAccessKey(DeleteAccessKeyRequest deleteAccessKeyRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteAccountAliasResult deleteAccountAlias(DeleteAccountAliasRequest deleteAccountAliasRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteAccountPasswordPolicyResult deleteAccountPasswordPolicy(
			DeleteAccountPasswordPolicyRequest deleteAccountPasswordPolicyRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteAccountPasswordPolicyResult deleteAccountPasswordPolicy() {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteGroupResult deleteGroup(DeleteGroupRequest deleteGroupRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteGroupPolicyResult deleteGroupPolicy(DeleteGroupPolicyRequest deleteGroupPolicyRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteInstanceProfileResult deleteInstanceProfile(
			DeleteInstanceProfileRequest deleteInstanceProfileRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteLoginProfileResult deleteLoginProfile(DeleteLoginProfileRequest deleteLoginProfileRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteOpenIDConnectProviderResult deleteOpenIDConnectProvider(
			DeleteOpenIDConnectProviderRequest deleteOpenIDConnectProviderRequest) {
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
	public DeleteServerCertificateResult deleteServerCertificate(
			DeleteServerCertificateRequest deleteServerCertificateRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteServiceLinkedRoleResult deleteServiceLinkedRole(
			DeleteServiceLinkedRoleRequest deleteServiceLinkedRoleRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteServiceSpecificCredentialResult deleteServiceSpecificCredential(
			DeleteServiceSpecificCredentialRequest deleteServiceSpecificCredentialRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteSigningCertificateResult deleteSigningCertificate(
			DeleteSigningCertificateRequest deleteSigningCertificateRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteUserResult deleteUser(DeleteUserRequest deleteUserRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteUserPolicyResult deleteUserPolicy(DeleteUserPolicyRequest deleteUserPolicyRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public DeleteVirtualMFADeviceResult deleteVirtualMFADevice(
			DeleteVirtualMFADeviceRequest deleteVirtualMFADeviceRequest) {
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
	public GenerateCredentialReportResult generateCredentialReport(
			GenerateCredentialReportRequest generateCredentialReportRequest) {
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
	public GetAccountAuthorizationDetailsResult getAccountAuthorizationDetails(
			GetAccountAuthorizationDetailsRequest getAccountAuthorizationDetailsRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public GetAccountAuthorizationDetailsResult getAccountAuthorizationDetails() {
        throw new UnsupportedOperationException();
	}

	@Override
	public GetAccountPasswordPolicyResult getAccountPasswordPolicy(
			GetAccountPasswordPolicyRequest getAccountPasswordPolicyRequest) {
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
	public GetContextKeysForCustomPolicyResult getContextKeysForCustomPolicy(
			GetContextKeysForCustomPolicyRequest getContextKeysForCustomPolicyRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public GetContextKeysForPrincipalPolicyResult getContextKeysForPrincipalPolicy(
			GetContextKeysForPrincipalPolicyRequest getContextKeysForPrincipalPolicyRequest) {
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
	public GetOpenIDConnectProviderResult getOpenIDConnectProvider(
			GetOpenIDConnectProviderRequest getOpenIDConnectProviderRequest) {
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
	public GetServiceLinkedRoleDeletionStatusResult getServiceLinkedRoleDeletionStatus(
			GetServiceLinkedRoleDeletionStatusRequest getServiceLinkedRoleDeletionStatusRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public GetUserResult getUser(GetUserRequest getUserRequest) {
        throw new UnsupportedOperationException();
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
       Collection<AccessKeyMetadata> accessKeyMetadata = new ArrayList<AccessKeyMetadata>();
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
	public ListAttachedGroupPoliciesResult listAttachedGroupPolicies(
			ListAttachedGroupPoliciesRequest listAttachedGroupPoliciesRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public ListAttachedRolePoliciesResult listAttachedRolePolicies(
			ListAttachedRolePoliciesRequest listAttachedRolePoliciesRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public ListAttachedUserPoliciesResult listAttachedUserPolicies(
			ListAttachedUserPoliciesRequest listAttachedUserPoliciesRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public ListEntitiesForPolicyResult listEntitiesForPolicy(
			ListEntitiesForPolicyRequest listEntitiesForPolicyRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public ListGroupPoliciesResult listGroupPolicies(ListGroupPoliciesRequest listGroupPoliciesRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public ListGroupsResult listGroups(ListGroupsRequest listGroupsRequest) {
        throw new UnsupportedOperationException();
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
	public ListInstanceProfilesForRoleResult listInstanceProfilesForRole(
			ListInstanceProfilesForRoleRequest listInstanceProfilesForRoleRequest) {
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
	public ListOpenIDConnectProvidersResult listOpenIDConnectProviders(
			ListOpenIDConnectProvidersRequest listOpenIDConnectProvidersRequest) {
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
	public ListServerCertificatesResult listServerCertificates(
			ListServerCertificatesRequest listServerCertificatesRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public ListServerCertificatesResult listServerCertificates() {
        throw new UnsupportedOperationException();
	}

	@Override
	public ListServiceSpecificCredentialsResult listServiceSpecificCredentials(
			ListServiceSpecificCredentialsRequest listServiceSpecificCredentialsRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public ListSigningCertificatesResult listSigningCertificates(
			ListSigningCertificatesRequest listSigningCertificatesRequest) {
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
        throw new UnsupportedOperationException();
	}

	@Override
	public ListVirtualMFADevicesResult listVirtualMFADevices(
			ListVirtualMFADevicesRequest listVirtualMFADevicesRequest) {
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
	public RemoveClientIDFromOpenIDConnectProviderResult removeClientIDFromOpenIDConnectProvider(
			RemoveClientIDFromOpenIDConnectProviderRequest removeClientIDFromOpenIDConnectProviderRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public RemoveRoleFromInstanceProfileResult removeRoleFromInstanceProfile(
			RemoveRoleFromInstanceProfileRequest removeRoleFromInstanceProfileRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public RemoveUserFromGroupResult removeUserFromGroup(RemoveUserFromGroupRequest removeUserFromGroupRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public ResetServiceSpecificCredentialResult resetServiceSpecificCredential(
			ResetServiceSpecificCredentialRequest resetServiceSpecificCredentialRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public ResyncMFADeviceResult resyncMFADevice(ResyncMFADeviceRequest resyncMFADeviceRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public SetDefaultPolicyVersionResult setDefaultPolicyVersion(
			SetDefaultPolicyVersionRequest setDefaultPolicyVersionRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public SimulateCustomPolicyResult simulateCustomPolicy(SimulateCustomPolicyRequest simulateCustomPolicyRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public SimulatePrincipalPolicyResult simulatePrincipalPolicy(
			SimulatePrincipalPolicyRequest simulatePrincipalPolicyRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public UpdateAccessKeyResult updateAccessKey(UpdateAccessKeyRequest updateAccessKeyRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public UpdateAccountPasswordPolicyResult updateAccountPasswordPolicy(
			UpdateAccountPasswordPolicyRequest updateAccountPasswordPolicyRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public UpdateAssumeRolePolicyResult updateAssumeRolePolicy(
			UpdateAssumeRolePolicyRequest updateAssumeRolePolicyRequest) {
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
	public UpdateOpenIDConnectProviderThumbprintResult updateOpenIDConnectProviderThumbprint(
			UpdateOpenIDConnectProviderThumbprintRequest updateOpenIDConnectProviderThumbprintRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public UpdateRoleDescriptionResult updateRoleDescription(
			UpdateRoleDescriptionRequest updateRoleDescriptionRequest) {
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
	public UpdateServerCertificateResult updateServerCertificate(
			UpdateServerCertificateRequest updateServerCertificateRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public UpdateServiceSpecificCredentialResult updateServiceSpecificCredential(
			UpdateServiceSpecificCredentialRequest updateServiceSpecificCredentialRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public UpdateSigningCertificateResult updateSigningCertificate(
			UpdateSigningCertificateRequest updateSigningCertificateRequest) {
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
	public UploadServerCertificateResult uploadServerCertificate(
			UploadServerCertificateRequest uploadServerCertificateRequest) {
        throw new UnsupportedOperationException();
	}

	@Override
	public UploadSigningCertificateResult uploadSigningCertificate(
			UploadSigningCertificateRequest uploadSigningCertificateRequest) {
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
