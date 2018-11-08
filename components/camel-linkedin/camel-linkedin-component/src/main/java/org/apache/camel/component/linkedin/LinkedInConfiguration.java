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
package org.apache.camel.component.linkedin;

import java.util.Arrays;
import java.util.Map;

import org.apache.camel.component.linkedin.api.OAuthScope;
import org.apache.camel.component.linkedin.api.OAuthSecureStorage;
import org.apache.camel.component.linkedin.internal.LinkedInApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Component configuration for LinkedIn component.
 */
@UriParams
public class LinkedInConfiguration {

    @UriPath(enums = "comments,companies,groups,jobs,people,posts,search")
    @Metadata(required = "true")
    private LinkedInApiName apiName;
    @UriPath(enums = "addActivity,addComment,addCompanyUpdateComment,addCompanyUpdateCommentAsCompany,addGroupMembership,addInvite"
            + ",addJob,addJobBookmark,addPost,addShare,addUpdateComment,editJob,flagCategory,followCompany,followPost,getComment"
            + ",getCompanies,getCompanyById,getCompanyByName,getCompanyUpdateComments,getCompanyUpdateLikes,getCompanyUpdates"
            + ",getConnections,getConnectionsById,getConnectionsByUrl,getFollowedCompanies,getGroup,getGroupMemberships,getGroupMembershipSettings"
            + ",getHistoricalFollowStatistics,getHistoricalStatusUpdateStatistics,getJob,getJobBookmarks,getNetworkStats,getNetworkUpdates"
            + ",getNetworkUpdatesById,getNumberOfFollowers,getPerson,getPersonById,getPersonByUrl,getPost,getPostComments,getPosts"
            + ",getStatistics,getSuggestedCompanies,getSuggestedGroupPosts,getSuggestedGroups,getSuggestedJobs,getUpdateComments"
            + ",getUpdateLikes,isShareEnabled,isViewerShareEnabled,likeCompanyUpdate,likePost,likeUpdate,removeComment,removeGroupMembership"
            + ",removeGroupSuggestion,removeJob,removeJobBookmark,removePost,searchCompanies,searchJobs,searchPeople,share,stopFollowingCompany,updateGroupMembership")
    @Metadata(required = "true")
    private String methodName;
    @UriParam
    private String userName;
    @UriParam
    private String userPassword;
    @UriParam
    private OAuthSecureStorage secureStorage;
    @UriParam
    private String accessToken;
    @UriParam
    private Long expiryTime;
    @UriParam
    private String clientId;
    @UriParam
    private String clientSecret;
    @UriParam
    private OAuthScope[] scopes;
    @UriParam
    private String redirectUri;
    @UriParam
    private Map<String, Object> httpParams;
    @UriParam(defaultValue = "true")
    private boolean lazyAuth = true;

    public LinkedInApiName getApiName() {
        return apiName;
    }

    /**
     * What kind of operation to perform
     */
    public void setApiName(LinkedInApiName apiName) {
        this.apiName = apiName;
    }

    public String getMethodName() {
        return methodName;
    }

    /**
     * What sub operation to use for the selected operation
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getUserName() {
        return userName;
    }

    /**
     * LinkedIn user account name, MUST be provided
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPassword() {
        return userPassword;
    }

    /**
     * LinkedIn account password
     */
    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    public OAuthSecureStorage getSecureStorage() {
        return secureStorage;
    }

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * LinkedIn access token to avoid username and password login.
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Long getExpiryTime() {
        return expiryTime;
    }

    /**
     * LinkedIn access token expiry time in milliseconds since Unix Epoch, default is 60 days in the future.
     */
    public void setExpiryTime(Long expiryTime) {
        this.expiryTime = expiryTime;
    }

    /**
     * Callback interface for providing an OAuth token or to store the token generated by the component.
     * The callback should return null on the first call and then save the created token in the saveToken() callback.
     * If the callback returns null the first time, a userPassword MUST be provided
     */
    public void setSecureStorage(OAuthSecureStorage secureStorage) {
        this.secureStorage = secureStorage;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * LinkedIn application client ID
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * LinkedIn application client secret
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public OAuthScope[] getScopes() {
        return scopes;
    }

    /**
     * List of LinkedIn scopes as specified at https://developer.linkedin.com/documents/authentication#granting
     */
    public void setScopes(OAuthScope[] scopes) {
        this.scopes = scopes;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    /**
     * Application redirect URI, although the component never redirects to this page to avoid having to have a functioning redirect server.
     * So for testing one could use https://localhost
     */
    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public Map<String, Object> getHttpParams() {
        return httpParams;
    }

    /**
     * Custom HTTP params, for example proxy host and port, use constants from AllClientPNames
     */
    public void setHttpParams(Map<String, Object> httpParams) {
        this.httpParams = httpParams;
    }

    public boolean isLazyAuth() {
        return lazyAuth;
    }

    /**
     * Flag to enable/disable lazy OAuth, default is true. when enabled, OAuth token retrieval or generation is not done until the first REST call
     */
    public void setLazyAuth(boolean lazyAuth) {
        this.lazyAuth = lazyAuth;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LinkedInConfiguration) {
            final LinkedInConfiguration other = (LinkedInConfiguration) obj;
            return (userName == null ? other.userName == null : userName.equals(other.userName))
                && (userPassword == null ? other.userPassword == null : userPassword.equals(other.userPassword))
                && secureStorage == other.secureStorage
                && (clientId == null ? other.clientId == null : clientId.equals(other.clientId))
                && (clientSecret == null ? other.clientSecret == null : clientSecret.equals(other.clientSecret))
                && (redirectUri == null ? other.redirectUri == null : redirectUri.equals(other.redirectUri))
                && Arrays.equals(scopes, other.scopes)
                && (httpParams == null ? other.httpParams == null : httpParams.equals(other.httpParams))
                && (lazyAuth == other.lazyAuth);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(userName).append(userPassword).append(secureStorage)
            .append(clientId).append(clientSecret)
            .append(redirectUri).append(scopes).append(httpParams).append(lazyAuth).toHashCode();
    }

    public void validate() throws IllegalArgumentException {
        ObjectHelper.notEmpty(userName, "userName");
        if (ObjectHelper.isEmpty(userPassword) && secureStorage == null) {
            throw new IllegalArgumentException("Property userPassword or secureStorage is required");
        }
        ObjectHelper.notEmpty(clientId, "clientId");
        ObjectHelper.notEmpty(clientSecret, "clientSecret");
        ObjectHelper.notEmpty(redirectUri, "redirectUri");
    }
}
