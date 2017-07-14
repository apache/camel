## Twitter Salesforce Example

This example listen for other twitter users whom are mentioning you in their tweets.
Each twitter user is then created/updated as a contact in Salesforce.

This is an example that uses the `twitter-mention` and `salesforce-upsert-contact` Camel connectors. These connectors
are used as if they are regular Camel components in Camel routes.

See the `MentionAddContractRoute` class for more details.

### How to run

This example can be run from the command line using:

    mvn spring-boot:run
    
### Configuring Credentials

The example uses the `@CamelTweet` twitter account for connecting to twitter.
And uses the Apache Camel Salesforce testing account to connect to Salesforce.

You can configure your own accounts in the `application.properties` file.
