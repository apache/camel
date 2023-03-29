# Maven plugin for camel-salesforce component #

This plugin generates DTOs for use with the [Camel Salesforce Component](https://github.com/apache/camel/tree/main/components/camel-salesforce/camel-salesforce-component).  

## Usage ##
              
This plugin provides three maven goals:
                         
* The `generate` goal generates DTOs for use with the REST API.
* The `generatePubSub` goal generates Apache Avro `SpecificRecord` subclasses for use with the PubSub API.
* The `schema` goal generates JSON Schemas that correspond to objects used with the REST API.

The plugin configuration has the following properties.

* clientId - Salesforce client Id for Remote API access
* clientSecret - Salesforce client secret for Remote API access
* userName - Salesforce account user name
* password - Salesforce account password (including secret token)
* loginUrl - Salesforce loginUrl (defaults to "https://login.salesforce.com")
* version - Salesforce Rest API version, defaults to 25.0
* outputDirectory - Directory where to place generated DTOs, defaults to ${project.build.directory}/generated-sources/camel-salesforce
* includes - List of SObject types to include
* topics - List of topics to include, .e.g., `/event/BatchApexErrorEvent`. This property only applies to the `generatePubSub` goal.
* excludes - List of SObject types to exclude
* includePattern - Java RegEx for SObject types to include
* excludePattern - Java RegEx for SObject types to exclude
* packageName - Java package name for generated DTOs, defaults to org.apache.camel.salesforce.dto.
* customTypes - override default types in generated DTOs
* useStringsForPicklists - Use strings instead of enumerations for picklists. Default is false.
* childRelationshipNameSuffix - Suffix for child relationship property name. Necessary if an SObject
has a lookup field with the same name as its Child Relationship Name. If setting to something other 
than default, "List" is a sensible value.
* enumerationOverrideProperties - Override picklist enum value generation via a java.util.Properties instance. 
Property name format: `SObject.FieldName.PicklistValue`. Property value is the desired enum value. E.g.:
    ```
    <enumerationOverrideProperties>
      <property>
        <name>Student__c.FinalGrade__c.A-</name>
        <value>AMinus</value>
      </property>
    </enumerationOverrideProperties>
    ```

Additonal properties to provide proxy information, if behind a firewall.

* httpProxyHost
* httpProxyPort
* httpProxyUsername
* httpProxyPassword
* httpProxyRealm
* httpProxyAuthUri
* httpProxyUseDigestAuth
* httpProxyIncludedAddresses
* httpProxyExcludedAddresses

Sample pom.xml
```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">

	<properties>
	    
		<camelSalesforce.clientId>5MVG9uudbyLbNPZOFutIHJpIb2nchnCiNE_NqeYcewMCPPT8_6VV_LQF_CJ813456GxzhxZdxlGwbYI_yzHmz</camelSalesforce.clientId>
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
			
			<!-- camel maven saleforce for creating salesforce objects -->
			<plugin>
				<groupId>org.apache.camel.maven</groupId>
				<artifactId>camel-salesforce-maven-plugin</artifactId>
				<version>2.17.1</version>
				<configuration>
					<clientId>${camelSalesforce.clientId}</clientId>
					<clientSecret>${camelSalesforce.clientSecret}</clientSecret>
					<userName>${camelSalesforce.userName}</userName>
					<password>${camelSalesforce.password}</password>
					<loginUrl default-value="https://login.salesforce.com">${camelSalesforce.loginUrl}</loginUrl> 
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
````
