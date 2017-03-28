# Twitter Salesforce Example

### Introduction

This example listen for tweets which are mentioning the `@cameltweet` twitter account.
Information from the user whom mentions is then created as a contact in Salesforce.

### Configuring Twitter

This example is already configured using a testing purpose twitter account named 'cameltweet'.
And therefore the example is ready to run out of the box.

This account is only for testing purpose, and should not be used in your custom applications.
For that you need to setup and use your own twitter account.

### Configuring Salesforce

This example uses Camels own test Salesforce developer account, you would most likely want to
sign up with your own Developer account at <https://developer.salesforce.com/>. After you
have done that, you'll need to create a Connected Application for your integration.

To do this after logging in to your Salesforce Developer account, navigate to _Apps_ located
under _Build_ and then _Create_, there you should see _Connected Apps_ table in the heading
click on _New_ and fill in the indicated required fields and enable the _OAuth Settings_, for
_Callback URL_ you can use <https://login.salesforce.com/services/oauth2/success>.

In the _Available OAuth Scopes_ add _Access and manage your data (api)_ and 
_Perform requests on your behalf at any time (refresh_token, offline_access)_.

After clicking _Save_ click on _Manage_ on the top of the page and then click on
 _Edit Policies_. Change the _IP Relaxation_ to _Relax IP restrictions_ and click on _Save_.

**NOTE:** This will get you started quicker, but production you should re-evaluate to comply 
with your security needs.

Next gather your _Consumer Key_ (_clientId_ property), _Consumer Secret_ (clientSecret) and
either use username and password of the developer account; or get the refresh token from
Salesforce (more on this below).

#### Adding the Twitter screen name custom field

The example adds a custom field to _Contact_ SObject, to add it to your Salesforce environment
go into _Customize_ under _Build_ and choose _Fields_ under _Contact_.

In _Contact Custom Fields & Relationships_ click on _New_ and add a field of type `Text`
with field label `Twitter Screen Name`, length of 15 and for uniqueness select 
_Do not allow duplicate values_ and set the
_Set this field as the unique record identifier from an external system_.

#### Getting the OAuth refresh token

In your browser go to the URL change the `__YOUR_CLIENT_ID_HERE__` with your connected
application _Consumer Key_:

<https://login.salesforce.com/services/oauth2/authorize?response_type=token&client_id=__YOUR_CLIENT_ID_HERE__&redirect_uri=https://login.salesforce.com/services/oauth2/success&display=touch>

Allow access to the application, and you'll end up on a page with `refresh_token` after
the `#`, something like:

`https://login.salesforce.com/services/oauth2/success#access_token=..&refresh_token=`**<refresh_token>**`&instance_url=...&issued_at=...&signature=...&scope=...&token_type=Bearer`

#### How to generate Salesforce Data Transfer Objects (DTOs)

The best way to generate Java representation of Salesforce SObjects is to use the
`camel-salesforce-maven-plugin`, for example:

    $ mvn org.apache.camel.maven:camel-salesforce-maven-plugin:generate \
      -DcamelSalesforce.clientId=<client id> \
      -DcamelSalesforce.clientSecret=<client secret> \
      -DcamelSalesforce.userName=<username> \
      -DcamelSalesforce.password=<password>

You can specify the only the SObjects you'll integrate with using `camelSalesforce.includePattern` parameter, like:

    $ mvn org.apache.camel.maven:camel-salesforce-maven-plugin:generate \
      -DcamelSalesforce.clientId=<client id> \
      -DcamelSalesforce.clientSecret=<client secret> \
      -DcamelSalesforce.userName=<username> \
      -DcamelSalesforce.password=<password> \
      -DcamelSalesforce.includePattern=Contact

To generate only DTOs needed for Contact, but the parameter value can be specified using regular expressions.

### Build

You will need to compile this example first:

	mvn compile

### Run

To run the example type

	mvn spring-boot:run

To stop the example hit <kbd>ctrl</kbd>+<kbd>c</kbd>


### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
	<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may
have.  Enjoy!


The Camel riders!
