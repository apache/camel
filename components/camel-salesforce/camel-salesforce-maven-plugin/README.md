# Maven plugin for camel-salesforce component #

This plugin generates DTOs for use with the [Camel Salesforce Component](https://github.com/apache/camel/tree/main/components/camel-salesforce/camel-salesforce-component).  

## Usage ##
              
This plugin provides three Maven goals:
                         
* The `generate` goal generates DTOs for use with the REST API.
* The `generatePubSub` goal generates Apache Avro `SpecificRecord` subclasses for use with the PubSub API.
* The `schema` goal generates JSON Schemas that correspond to objects used with the REST API.

The plugin configuration has the following properties.

* `clientId` - Salesforce client Id for Remote API access
* `clientSecret` - Salesforce client secret for Remote API access
* `userName` - Salesforce account username (required for Username-Password and JWT flows)
* `password` - Salesforce account password (including secret token)
* `authenticationType` - Salesforce authentication type: USERNAME_PASSWORD, JWT, or CLIENT_CREDENTIALS. If not specified, auto-detected from provided credentials.
* `jwtAudience` - Salesforce JWT audience (defaults to "https://login.salesforce.com")
* `keystoreResource` - Path to keystore file for JWT authentication
* `keystorePassword` - Password for keystore file
* `keystoreType` - Type of keystore file (defaults to "JKS")
* `loginUrl` - Salesforce loginUrl (defaults to "https://login.salesforce.com")
* `version` - Salesforce Rest API version, defaults to 25.0
* `outputDirectory` - Directory where to place generated DTOs, defaults to ${project.build.directory}/generated-sources/camel-salesforce
* `includes` - List of SObject types to include
* `topics` - List of topics to include, e.g., `/event/BatchApexErrorEvent`. This property only applies to the `generatePubSub` goal.
* `excludes` - List of SObject types to exclude
* `includePattern` - Java RegEx for SObject types to include
* `excludePattern` - Java RegEx for SObject types to exclude
* `packageName` - Java package name for generated DTOs, defaults to org.apache.camel.salesforce.dto.
* `customTypes` - override default types in generated DTOs
* `useStringsForPicklists` - Use strings instead of enumerations for picklists. Default is false.
* `childRelationshipNameSuffix` - Suffix for child relationship property name. Necessary if an SObject
has a lookup field with the same name as its Child Relationship Name. If setting to something other 
than default, "List" is a sensible value.
* `enumerationOverrideProperties` - Override picklist enum value generation via a java.util.Properties instance. 
Property name format: `SObject.FieldName.PicklistValue`. Property value is the desired enum value. E.g.:
    ```
    <enumerationOverrideProperties>
      <property>
        <name>Student__c.FinalGrade__c.A-</name>
        <value>AMinus</value>
      </property>
    </enumerationOverrideProperties>
    ```

Additional properties to provide proxy information, if behind a firewall.

* `httpProxyHost`
* `httpProxyPort`
* `httpProxyUsername`
* `httpProxyPassword`
* `httpProxyRealm`
* `httpProxyAuthUri`
* `httpProxyUseDigestAuth`
* `httpProxyIncludedAddresses`
* `httpProxyExcludedAddresses`

Three authentication methods are supported by the plugin: Username-Password, JWT, and Client Credentials.
The plugin auto-detects the authentication method from the provided credentials, or you can set `authenticationType` explicitly.

* **Username-Password** requires: `clientId`, `clientSecret`, `userName`, and `password`.<br>
  Auto-detected when `password` is specified.
* **JWT** requires: `clientId`, `userName`, `loginUrl` (My Domain URL), `keystoreResource`, and `keystorePassword`.<br>
  `keystoreType` defaults to JKS, `jwtAudience` defaults to `https://login.salesforce.com`.<br>
  Auto-detected when `keystoreResource` is specified.
* **Client Credentials** requires: `clientId`, `clientSecret`, and `loginUrl` (My Domain URL).<br>
  Auto-detected when only `clientId` and `clientSecret` are specified (no `password`, no `userName`, no `keystoreResource`).

___
<br>

### Username-Password Authentication Type ###

Sample pom.xml using **Username-Password** authentication:
```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">

	<properties>
		<camelSalesforce.clientId>5MVG9uudbyLbNPZOFut...GwbYI_yzHmz</camelSalesforce.clientId>
		<camelSalesforce.clientSecret>5630289243049151316</camelSalesforce.clientSecret>
		<camelSalesforce.userName>foo@bar.com</camelSalesforce.userName>
		<camelSalesforce.password>foopasswordCbe5V27JxD0JXYFGJIdIEWB7p</camelSalesforce.password>
		<camelSalesforce.loginUrl>https://myDomain.my.salesforce.com</camelSalesforce.loginUrl>
		<camelSalesforce.httpProxyHost>foo.bar.com</camelSalesforce.httpProxyHost>
		<camelSalesforce.httpProxyPort>8090</camelSalesforce.httpProxyPort>
	</properties>
	...
	<build>
		...
		<plugins>
			...
			<!-- camel maven salesforce for creating salesforce objects -->
			<plugin>
				<groupId>org.apache.camel.maven</groupId>
				<artifactId>camel-salesforce-maven-plugin</artifactId>
				<version>${camel.version}</version>
				<configuration>
					<clientId>${camelSalesforce.clientId}</clientId>
					<clientSecret>${camelSalesforce.clientSecret}</clientSecret>
					<userName>${camelSalesforce.userName}</userName>
					<password>${camelSalesforce.password}</password>
					<loginUrl>${camelSalesforce.loginUrl}</loginUrl>
					<includes>
						<include>Account</include>
						<include>Contacts</include>
					</includes>
					<httpProxyHost>${camelSalesforce.httpProxyHost}</httpProxyHost>
					<httpProxyPort>${camelSalesforce.httpProxyPort}</httpProxyPort>
				</configuration>
			</plugin>
		</plugins>
	</build>

</project>
```
For obvious security reasons it is recommended that the clientId, clientSecret, userName and password fields be not set in the pom.xml. 
The plugin should be configured for the rest of the properties, and can be executed using the following command:

	mvn camel-salesforce:generate -DcamelSalesforce.clientId=<clientid> -DcamelSalesforce.clientSecret=<clientsecret> -DcamelSalesforce.userName=<username> -DcamelSalesforce.password=<password>

___
<br>

###  JWT Authentication Type ###

Sample pom.xml using **JWT** authentication:
```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">

	<properties>
		<camelSalesforce.clientId>5MVG9uudbyLbNPZOFut...GwbYI_yzHmz</camelSalesforce.clientId>
		<camelSalesforce.userName>foo@bar.com</camelSalesforce.userName>
		<camelSalesforce.keystore.resource>src/main/resources/salesforce.jks</camelSalesforce.keystore.resource>
		<camelSalesforce.keystore.password>foopasswordCbe5V27JxD0JXYFGJIdIEWB7p</camelSalesforce.keystore.password>
		<camelSalesforce.keystore.type>JKS</camelSalesforce.keystore.type>
		<camelSalesforce.jwtAudience>https://login.salesforce.com</camelSalesforce.jwtAudience>
		<camelSalesforce.loginUrl>https://myDomain.my.salesforce.com</camelSalesforce.loginUrl>
		<camelSalesforce.httpProxyHost>foo.bar.com</camelSalesforce.httpProxyHost>
		<camelSalesforce.httpProxyPort>8090</camelSalesforce.httpProxyPort>
	</properties>
	...
	<build>
		...
		<plugins>
			...
			<!-- camel maven salesforce for creating salesforce objects -->
			<plugin>
				<groupId>org.apache.camel.maven</groupId>
				<artifactId>camel-salesforce-maven-plugin</artifactId>
				<version>${camel.version}</version>
				<configuration>
					<clientId>${camelSalesforce.clientId}</clientId>
					<userName>${camelSalesforce.userName}</userName>
					<keystoreResource>${camelSalesforce.keystore.resource}</keystoreResource>
					<keystorePassword>${camelSalesforce.keystore.password}</keystorePassword>
					<keystoreType>${camelSalesforce.keystore.type}</keystoreType>
					<jwtAudience>${camelSalesforce.jwtAudience}</jwtAudience>
					<loginUrl>${camelSalesforce.loginUrl}</loginUrl>
					<includes>
						<include>Account</include>
						<include>Contacts</include>
					</includes>
					<httpProxyHost>${camelSalesforce.httpProxyHost}</httpProxyHost>
					<httpProxyPort>${camelSalesforce.httpProxyPort}</httpProxyPort>
				</configuration>
			</plugin>
		</plugins>
	</build>

</project>
```
For obvious security reasons it is recommended that the clientId, userName, keystoreResource, keystorePassword, keystoreType and jwtAudience fields be not set in the pom.xml. 
The plugin should be configured for the rest of the properties, and can be executed using the following command:

	mvn camel-salesforce:generate -DcamelSalesforce.clientId=<clientid> -DcamelSalesforce.userName=<username> -DcamelSalesforce.keystore.resource=<keystoreResource> -DcamelSalesforce.keystore.password=<keystorePassword> -DcamelSalesforce.keystore.type=<keystoreType> -DcamelSalesforce.jwtAudience=<jwtAudience> -DcamelSalesforce.loginUrl=<login-url>

___
<br>

### Client Credentials Authentication Type ###

Sample pom.xml using **Client Credentials** authentication
```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">

	<properties>
		<camelSalesforce.clientId>5MVG9uudbyLbNPZOFut...GwbYI_yzHmz</camelSalesforce.clientId>
		<camelSalesforce.clientSecret>5630289243049151316</camelSalesforce.clientSecret>
		<camelSalesforce.loginUrl>https://myDomain.my.salesforce.com</camelSalesforce.loginUrl>
	</properties>
	...
	<build>
		...
		<plugins>
			...
			<!-- camel maven salesforce for creating salesforce objects -->
			<plugin>
				<groupId>org.apache.camel.maven</groupId>
				<artifactId>camel-salesforce-maven-plugin</artifactId>
				<version>${camel.version}</version>
				<configuration>
					<clientId>${camelSalesforce.clientId}</clientId>
					<clientSecret>${camelSalesforce.clientSecret}</clientSecret>
					<loginUrl>${camelSalesforce.loginUrl}</loginUrl>
					<includes>
						<include>Account</include>
						<include>Contacts</include>
					</includes>
				</configuration>
			</plugin>
		</plugins>
	</build>

</project>
```
For obvious security reasons it is recommended that the clientId and clientSecret fields be not set in the pom.xml.
The plugin should be configured for the rest of the properties, and can be executed using the following command:

	mvn camel-salesforce:generate -DcamelSalesforce.clientId=<clientid> -DcamelSalesforce.clientSecret=<clientsecret> -DcamelSalesforce.loginUrl=<login-url>


___

The generated DTOs use Jackson. All Salesforce field types are supported. Date and time fields are mapped to java.time.ZonedDateTime, and picklist fields are mapped to generated Java Enumerations.

Relationship fields, e.g. `Contact.Account`, will be strongly typed if the referenced SObject type is listed in `includes`. Otherwise, the type of the reference object will be `AbstractDescribedSObjectBase`. Some useful but non-obvious SObjects to include are `RecordType`, `User`, `Group`, and `Name`.  

[Polymorphic relationship fields](https://developer.salesforce.com/docs/atlas.en-us.232.0.soql_sosl.meta/soql_sosl/sforce_api_calls_soql_relationships_and_polymorph_keys.htm) will have the type `AbstractDescribedSObjectBase`, however at runtime, query results
will be serialized to the specific type if that type was in `includes` and a DTO was generated for it. Note that 
the query must be written to return type-specific fields, e.g.:

```
SELECT Id, Name, Typeof Owner WHEN User Then FirstName, LastName, Username End FROM Line_Item__c
```

You can customize types, i.e. use java.time.LocalDateTime instead of the default java.time.ZonedDateTime by specifying the `customTypes` property like:

```xml
<plugin>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-salesforce-maven-plugin</artifactId>
  <configuration>
    <!-- ... -->
    <customTypes>
      <date>java.time.LocalDateTime</date>
    </customTypes>
  </configuration>
</plugin>
```
