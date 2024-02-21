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
package org.apache.camel.component.twitter;

import org.apache.camel.spi.Metadata;

/**
 * Defines common constants
 */
public interface TwitterConstants {

    // The schemes
    String SCHEME_TIMELINE = "twitter-timeline";
    String SCHEME_SEARCH = "twitter-search";
    String SCHEME_DIRECT_MESSAGE = "twitter-directmessage";

    @Metadata(description = "The keywords to search", javaType = "String", applicableFor = SCHEME_SEARCH)
    String TWITTER_KEYWORDS = "CamelTwitterKeywords";
    @Metadata(description = "The lang string ISO_639-1 which will be used for searching", javaType = "String",
              applicableFor = SCHEME_SEARCH)
    String TWITTER_SEARCH_LANGUAGE = "CamelTwitterSearchLanguage";
    @Metadata(description = "Limiting number of results per page.", javaType = "Integer", applicableFor = SCHEME_SEARCH)
    String TWITTER_COUNT = "CamelTwitterCount";
    @Metadata(description = "The number of pages result which you want camel-twitter to consume.", javaType = "Integer",
              applicableFor = SCHEME_SEARCH)
    String TWITTER_NUMBER_OF_PAGES = "CamelTwitterNumberOfPages";
    /**
     * The last tweet id which will be used for pulling the tweets. It is useful when the camel route is restarted after
     * a long-running.
     */
    @Metadata(javaType = "Long", applicableFor = SCHEME_SEARCH)
    String TWITTER_SINCEID = "CamelTwitterSinceId";
    @Metadata(description = "If specified, returns tweets with status ids less than the given id.", javaType = "Long",
              applicableFor = SCHEME_SEARCH)
    String TWITTER_MAXID = "CamelTwitterMaxId";
    @Metadata(description = "The user", javaType = "String", applicableFor = SCHEME_DIRECT_MESSAGE)
    String TWITTER_USER = "CamelTwitterUser";
    String TWITTER_USER_ROLE = "CamelTwitterUserRole";
    @Metadata(description = "The type of event. The supported values are the values of the enum " +
                            "org.apache.camel.component.twitter.consumer.TwitterEventType",
              javaType = "String")
    String TWITTER_EVENT_TYPE = "CamelTwitterEventType";
}
