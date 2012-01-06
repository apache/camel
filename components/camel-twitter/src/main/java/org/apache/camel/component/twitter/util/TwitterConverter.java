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
package org.apache.camel.component.twitter.util;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Converter;
import org.apache.camel.component.twitter.data.Status;

/**
 * Utility for converting between Twitter4J and camel-twitter data layers.
 * 
 */
@Converter
public final class TwitterConverter {

    private TwitterConverter() {
        // Helper class
    }

    @Converter
    public static String toString(Status status) throws ParseException {
        return status.toString();
    }

    @Converter
    public static Status convertStatus(twitter4j.Status s) {
        return new Status(s);
    }

    @Converter
    public static List<Status> convertStatuses(List<twitter4j.Status> ls) {
        List<Status> newLs = new ArrayList<Status>(ls.size());
        for (Iterator<twitter4j.Status> i = ls.iterator(); i.hasNext();) {
            newLs.add(convertStatus(i.next()));
        }
        return newLs;
    }
}
