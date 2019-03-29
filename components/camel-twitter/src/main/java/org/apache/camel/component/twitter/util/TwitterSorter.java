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
package org.apache.camel.component.twitter.util;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import twitter4j.Status;

/**
 * To sort tweets.
 */
public final class TwitterSorter {

    private TwitterSorter() {
    }

    /**
     * Sorts the tweets by {@link Status#getId()}.
     */
    public static List<Exchange> sortByStatusId(List<Exchange> exchanges) {
        return exchanges.stream().sorted((e1, e2) -> {
            Object b1 = e1.getIn().getBody();
            Object b2 = e2.getIn().getBody();
            if (b1 instanceof Status && b2 instanceof Status) {
                Status s1 = (Status) b1;
                Status s2 = (Status) b2;
                return Long.compare(s1.getId(), s2.getId());
            }
            return 0;
        }).collect(Collectors.toList());
    }
}
