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
package org.apache.camel.kotlin.components

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

public fun UriDsl.facebook(i: FacebookUriDsl.() -> Unit) {
  FacebookUriDsl(this).apply(i)
}

@CamelDslMarker
public class FacebookUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("facebook")
  }

  private var methodName: String = ""

  public fun methodName(methodName: String) {
    this.methodName = methodName
    it.url("$methodName")
  }

  public fun achievementURL(achievementURL: String) {
    it.property("achievementURL", achievementURL)
  }

  public fun albumId(albumId: String) {
    it.property("albumId", albumId)
  }

  public fun albumUpdate(albumUpdate: String) {
    it.property("albumUpdate", albumUpdate)
  }

  public fun appId(appId: String) {
    it.property("appId", appId)
  }

  public fun center(center: String) {
    it.property("center", center)
  }

  public fun checkinId(checkinId: String) {
    it.property("checkinId", checkinId)
  }

  public fun checkinUpdate(checkinUpdate: String) {
    it.property("checkinUpdate", checkinUpdate)
  }

  public fun clientURL(clientURL: String) {
    it.property("clientURL", clientURL)
  }

  public fun clientVersion(clientVersion: String) {
    it.property("clientVersion", clientVersion)
  }

  public fun commentId(commentId: String) {
    it.property("commentId", commentId)
  }

  public fun commentUpdate(commentUpdate: String) {
    it.property("commentUpdate", commentUpdate)
  }

  public fun debugEnabled(debugEnabled: String) {
    it.property("debugEnabled", debugEnabled)
  }

  public fun debugEnabled(debugEnabled: Boolean) {
    it.property("debugEnabled", debugEnabled.toString())
  }

  public fun description(description: String) {
    it.property("description", description)
  }

  public fun distance(distance: String) {
    it.property("distance", distance)
  }

  public fun distance(distance: Int) {
    it.property("distance", distance.toString())
  }

  public fun domainId(domainId: String) {
    it.property("domainId", domainId)
  }

  public fun domainName(domainName: String) {
    it.property("domainName", domainName)
  }

  public fun domainNames(domainNames: String) {
    it.property("domainNames", domainNames)
  }

  public fun eventId(eventId: String) {
    it.property("eventId", eventId)
  }

  public fun eventUpdate(eventUpdate: String) {
    it.property("eventUpdate", eventUpdate)
  }

  public fun friendId(friendId: String) {
    it.property("friendId", friendId)
  }

  public fun friendlistId(friendlistId: String) {
    it.property("friendlistId", friendlistId)
  }

  public fun friendlistName(friendlistName: String) {
    it.property("friendlistName", friendlistName)
  }

  public fun friendUserId(friendUserId: String) {
    it.property("friendUserId", friendUserId)
  }

  public fun groupId(groupId: String) {
    it.property("groupId", groupId)
  }

  public fun gzipEnabled(gzipEnabled: String) {
    it.property("gzipEnabled", gzipEnabled)
  }

  public fun gzipEnabled(gzipEnabled: Boolean) {
    it.property("gzipEnabled", gzipEnabled.toString())
  }

  public fun httpConnectionTimeout(httpConnectionTimeout: String) {
    it.property("httpConnectionTimeout", httpConnectionTimeout)
  }

  public fun httpConnectionTimeout(httpConnectionTimeout: Int) {
    it.property("httpConnectionTimeout", httpConnectionTimeout.toString())
  }

  public fun httpDefaultMaxPerRoute(httpDefaultMaxPerRoute: String) {
    it.property("httpDefaultMaxPerRoute", httpDefaultMaxPerRoute)
  }

  public fun httpDefaultMaxPerRoute(httpDefaultMaxPerRoute: Int) {
    it.property("httpDefaultMaxPerRoute", httpDefaultMaxPerRoute.toString())
  }

  public fun httpMaxTotalConnections(httpMaxTotalConnections: String) {
    it.property("httpMaxTotalConnections", httpMaxTotalConnections)
  }

  public fun httpMaxTotalConnections(httpMaxTotalConnections: Int) {
    it.property("httpMaxTotalConnections", httpMaxTotalConnections.toString())
  }

  public fun httpReadTimeout(httpReadTimeout: String) {
    it.property("httpReadTimeout", httpReadTimeout)
  }

  public fun httpReadTimeout(httpReadTimeout: Int) {
    it.property("httpReadTimeout", httpReadTimeout.toString())
  }

  public fun httpRetryCount(httpRetryCount: String) {
    it.property("httpRetryCount", httpRetryCount)
  }

  public fun httpRetryCount(httpRetryCount: Int) {
    it.property("httpRetryCount", httpRetryCount.toString())
  }

  public fun httpRetryIntervalSeconds(httpRetryIntervalSeconds: String) {
    it.property("httpRetryIntervalSeconds", httpRetryIntervalSeconds)
  }

  public fun httpRetryIntervalSeconds(httpRetryIntervalSeconds: Int) {
    it.property("httpRetryIntervalSeconds", httpRetryIntervalSeconds.toString())
  }

  public fun httpStreamingReadTimeout(httpStreamingReadTimeout: String) {
    it.property("httpStreamingReadTimeout", httpStreamingReadTimeout)
  }

  public fun httpStreamingReadTimeout(httpStreamingReadTimeout: Int) {
    it.property("httpStreamingReadTimeout", httpStreamingReadTimeout.toString())
  }

  public fun ids(ids: String) {
    it.property("ids", ids)
  }

  public fun inBody(inBody: String) {
    it.property("inBody", inBody)
  }

  public fun includeRead(includeRead: String) {
    it.property("includeRead", includeRead)
  }

  public fun includeRead(includeRead: Boolean) {
    it.property("includeRead", includeRead.toString())
  }

  public fun isHidden(isHidden: String) {
    it.property("isHidden", isHidden)
  }

  public fun isHidden(isHidden: Boolean) {
    it.property("isHidden", isHidden.toString())
  }

  public fun jsonStoreEnabled(jsonStoreEnabled: String) {
    it.property("jsonStoreEnabled", jsonStoreEnabled)
  }

  public fun jsonStoreEnabled(jsonStoreEnabled: Boolean) {
    it.property("jsonStoreEnabled", jsonStoreEnabled.toString())
  }

  public fun link(link: String) {
    it.property("link", link)
  }

  public fun linkId(linkId: String) {
    it.property("linkId", linkId)
  }

  public fun locale(locale: String) {
    it.property("locale", locale)
  }

  public fun mbeanEnabled(mbeanEnabled: String) {
    it.property("mbeanEnabled", mbeanEnabled)
  }

  public fun mbeanEnabled(mbeanEnabled: Boolean) {
    it.property("mbeanEnabled", mbeanEnabled.toString())
  }

  public fun message(message: String) {
    it.property("message", message)
  }

  public fun messageId(messageId: String) {
    it.property("messageId", messageId)
  }

  public fun metric(metric: String) {
    it.property("metric", metric)
  }

  public fun milestoneId(milestoneId: String) {
    it.property("milestoneId", milestoneId)
  }

  public fun name(name: String) {
    it.property("name", name)
  }

  public fun noteId(noteId: String) {
    it.property("noteId", noteId)
  }

  public fun notificationId(notificationId: String) {
    it.property("notificationId", notificationId)
  }

  public fun objectId(objectId: String) {
    it.property("objectId", objectId)
  }

  public fun offerId(offerId: String) {
    it.property("offerId", offerId)
  }

  public fun optionDescription(optionDescription: String) {
    it.property("optionDescription", optionDescription)
  }

  public fun pageId(pageId: String) {
    it.property("pageId", pageId)
  }

  public fun permissionName(permissionName: String) {
    it.property("permissionName", permissionName)
  }

  public fun permissions(permissions: String) {
    it.property("permissions", permissions)
  }

  public fun photoId(photoId: String) {
    it.property("photoId", photoId)
  }

  public fun pictureId(pictureId: String) {
    it.property("pictureId", pictureId)
  }

  public fun pictureId(pictureId: Int) {
    it.property("pictureId", pictureId.toString())
  }

  public fun pictureId2(pictureId2: String) {
    it.property("pictureId2", pictureId2)
  }

  public fun pictureId2(pictureId2: Int) {
    it.property("pictureId2", pictureId2.toString())
  }

  public fun pictureSize(pictureSize: String) {
    it.property("pictureSize", pictureSize)
  }

  public fun placeId(placeId: String) {
    it.property("placeId", placeId)
  }

  public fun postId(postId: String) {
    it.property("postId", postId)
  }

  public fun postUpdate(postUpdate: String) {
    it.property("postUpdate", postUpdate)
  }

  public fun prettyDebugEnabled(prettyDebugEnabled: String) {
    it.property("prettyDebugEnabled", prettyDebugEnabled)
  }

  public fun prettyDebugEnabled(prettyDebugEnabled: Boolean) {
    it.property("prettyDebugEnabled", prettyDebugEnabled.toString())
  }

  public fun queries(queries: String) {
    it.property("queries", queries)
  }

  public fun query(query: String) {
    it.property("query", query)
  }

  public fun questionId(questionId: String) {
    it.property("questionId", questionId)
  }

  public fun reading(reading: String) {
    it.property("reading", reading)
  }

  public fun readingOptions(readingOptions: String) {
    it.property("readingOptions", readingOptions)
  }

  public fun restBaseURL(restBaseURL: String) {
    it.property("restBaseURL", restBaseURL)
  }

  public fun scoreValue(scoreValue: String) {
    it.property("scoreValue", scoreValue)
  }

  public fun scoreValue(scoreValue: Int) {
    it.property("scoreValue", scoreValue.toString())
  }

  public fun size(size: String) {
    it.property("size", size)
  }

  public fun source(source: String) {
    it.property("source", source)
  }

  public fun subject(subject: String) {
    it.property("subject", subject)
  }

  public fun tabId(tabId: String) {
    it.property("tabId", tabId)
  }

  public fun tagUpdate(tagUpdate: String) {
    it.property("tagUpdate", tagUpdate)
  }

  public fun testUser1(testUser1: String) {
    it.property("testUser1", testUser1)
  }

  public fun testUser2(testUser2: String) {
    it.property("testUser2", testUser2)
  }

  public fun testUserId(testUserId: String) {
    it.property("testUserId", testUserId)
  }

  public fun title(title: String) {
    it.property("title", title)
  }

  public fun toUserId(toUserId: String) {
    it.property("toUserId", toUserId)
  }

  public fun toUserIds(toUserIds: String) {
    it.property("toUserIds", toUserIds)
  }

  public fun userId(userId: String) {
    it.property("userId", userId)
  }

  public fun userId1(userId1: String) {
    it.property("userId1", userId1)
  }

  public fun userId2(userId2: String) {
    it.property("userId2", userId2)
  }

  public fun userIds(userIds: String) {
    it.property("userIds", userIds)
  }

  public fun userLocale(userLocale: String) {
    it.property("userLocale", userLocale)
  }

  public fun useSSL(useSSL: String) {
    it.property("useSSL", useSSL)
  }

  public fun useSSL(useSSL: Boolean) {
    it.property("useSSL", useSSL.toString())
  }

  public fun videoBaseURL(videoBaseURL: String) {
    it.property("videoBaseURL", videoBaseURL)
  }

  public fun videoId(videoId: String) {
    it.property("videoId", videoId)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun httpProxyHost(httpProxyHost: String) {
    it.property("httpProxyHost", httpProxyHost)
  }

  public fun httpProxyPassword(httpProxyPassword: String) {
    it.property("httpProxyPassword", httpProxyPassword)
  }

  public fun httpProxyPort(httpProxyPort: String) {
    it.property("httpProxyPort", httpProxyPort)
  }

  public fun httpProxyPort(httpProxyPort: Int) {
    it.property("httpProxyPort", httpProxyPort.toString())
  }

  public fun httpProxyUser(httpProxyUser: String) {
    it.property("httpProxyUser", httpProxyUser)
  }

  public fun oAuthAccessToken(oAuthAccessToken: String) {
    it.property("oAuthAccessToken", oAuthAccessToken)
  }

  public fun oAuthAccessTokenURL(oAuthAccessTokenURL: String) {
    it.property("oAuthAccessTokenURL", oAuthAccessTokenURL)
  }

  public fun oAuthAppId(oAuthAppId: String) {
    it.property("oAuthAppId", oAuthAppId)
  }

  public fun oAuthAppSecret(oAuthAppSecret: String) {
    it.property("oAuthAppSecret", oAuthAppSecret)
  }

  public fun oAuthAuthorizationURL(oAuthAuthorizationURL: String) {
    it.property("oAuthAuthorizationURL", oAuthAuthorizationURL)
  }

  public fun oAuthPermissions(oAuthPermissions: String) {
    it.property("oAuthPermissions", oAuthPermissions)
  }
}
