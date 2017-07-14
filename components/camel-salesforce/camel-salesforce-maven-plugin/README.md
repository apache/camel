# Maven plugin for camel-salesforce component #

This plugin generates DTOs for the [Camel Salesforce Component](https://github.com/dhirajsb/camel-salesforce). 

## Usage ##

The plugin configuration has the following properties.

* clientId - Salesforce client Id for Remote API access
* clientSecret - Salesforce client secret for Remote API access
* userName - Salesforce account user name
* password - Salesforce account password (including secret token)
* loginUrl - Salesforce loginUrl (defaults to "https://login.salesforce.com")
* version - Salesforce Rest API version, defaults to 25.0
* outputDirectory - Directory where to place generated DTOs, defaults to ${project.build.directory}/generated-sources/camel-salesforce
* includes - List of SObject types to include
* excludes - List of SObject types to exclude
* includePattern - Java RegEx for SObject types to include
* excludePattern - Java RegEx for SObject types to exclude
* packageName - Java package name for generated DTOs, defaults to org.apache.camel.salesforce.dto.

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

The generated DTOs use Jackson and XStream annotations. All Salesforce field types are supported. Date and time fields are mapped to java.time.ZonedDateTime, and picklist fields are mapped to generated Java Enumerations.
