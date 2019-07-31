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
package org.apache.camel.component.yammer.utils;

import java.util.Scanner;

import org.apache.camel.component.yammer.scribe.YammerApi;
import org.scribe.builder.ServiceBuilder;
import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

/**
 * Use this to get an access token from yammer. You will need the 
 * consumer key and secret key for your app registered with yammer to do this.
 */
public final class YammerAccessCodeGenerator {

    private static final Token EMPTY_TOKEN = null;
    
    private YammerAccessCodeGenerator() {
    }
    
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);

        System.out.println("Paste the consumerKey here");
        System.out.print(">>");
        String apiKey = in.nextLine();

        System.out.println("Paste the consumerSecret here");
        System.out.print(">>");
        String apiSecret = in.nextLine();

        OAuthService service = new ServiceBuilder()
            .provider(YammerApi.class)
            .apiKey(apiKey)
            .apiSecret(apiSecret)
            .build();

        String authorizationUrl = service.getAuthorizationUrl(EMPTY_TOKEN);
        System.out
                .println("Go and authorize your app here (eg. in a web browser):");
        System.out.println(authorizationUrl);
        System.out.println("... and paste the authorization code here");
        System.out.print(">>");
        Verifier verifier = new Verifier(in.nextLine());

        System.out.println();

        Token accessToken = service.getAccessToken(EMPTY_TOKEN, verifier);
        System.out.println("Your Access Token is: " + accessToken);
        System.out.println();

        in.close();
    }

}
