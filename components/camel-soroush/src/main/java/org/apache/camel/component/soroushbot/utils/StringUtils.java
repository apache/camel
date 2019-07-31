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
package org.apache.camel.component.soroushbot.utils;

public final class StringUtils {
    protected StringUtils() {
    }

    /**
     * create ordinal value for each number. like 1st, 2nd, 3rd, 4th ...
     *
     * @param number
     * @return the ordinal value of {@code number}
     */
    public static String ordinal(int number) {
        return number % 100 == 11 || number % 100 == 12 || number % 100 == 13 ? number + "th" : number + new String[]{"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"}[number % 10];
    }
}
