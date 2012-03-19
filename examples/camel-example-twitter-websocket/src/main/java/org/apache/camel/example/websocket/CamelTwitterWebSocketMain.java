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
package org.apache.camel.example.websocket;

import org.apache.camel.main.Main;

/**
 * A main to start this example.
 */
public final class CamelTwitterWebSocketMain {

    // Twitter now requires the use of OAuth for all client application authentication.
    // In order to use camel-twitter with your account, you'll need to create a new application
    // within Twitter at https://dev.twitter.com/apps/new and grant the application access to your account.
    // Finally, generate your access token and secret.
    private static String consumerKey = "INSERT HERE";
    private static String consumerSecret = "INSERT HERE";
    private static String accessToken = "INSERT HERE";
    private static String accessTokenSecret = "INSERT HERE";

    private CamelTwitterWebSocketMain() {
        // utility class
    }

    public static void main(String[] args) throws Exception {
        if (consumerKey.equals("INSERT HERE")) {
            System.out.println("\n\n\n\n");
            System.err.println("============================================================================");
            System.err.println("Error you need to configure twitter application authentication before using.");
            System.err.println("See more details in this source code.");
            System.err.println("============================================================================");
            System.out.println("\n\n\n\n");
            System.exit(0);
        } else {
            System.out.println("\n\n\n\n");
            System.out.println("===============================================");
            System.out.println("Open your web browser on http://localhost:9090");
            System.out.println("Press ctrl+c to stop this example");
            System.out.println("===============================================");
            System.out.println("\n\n\n\n");
        }

        // create a new Camel Main so we can easily start Camel
        Main main = new Main();

        // enable hangup support which mean we detect when the JVM terminates, and stop Camel graceful
        main.enableHangupSupport();

        TwitterWebSocketRoute route = new TwitterWebSocketRoute();

        // setup twitter application authentication
        route.setAccessToken(accessToken);
        route.setAccessTokenSecret(accessTokenSecret);
        route.setConsumerKey(consumerKey);
        route.setConsumerSecret(consumerSecret);

        // poll for gaga, every 2nd second
        route.setSearchTerm("gaga");
        route.setDelay(2);

        // add our routes to Camel
        main.addRouteBuilder(route);

        // and run, which keeps blocking until we terminate the JVM (or stop CamelContext)
        main.run();
    }

}
