# Maven plugin for camel-salesforce component #

This plugin generates DTOs for the [Camel Salesforce Component](https://github.com/dhirajsb/camel-salesforce). 

## Usage ##

The plugin configuration has the following properties.

* clientId - Salesforce client Id for Remote API access
* clientSecret - Salesforce client secret for Remote API access
* userName - Salesforce account user name
* password - Salesforce account password (including secret token)
* version - Salesforce Rest API version, defaults to 25.0
* outputDirectory - Directory where to place generated DTOs, defaults to ${project.build.directory}/generated-sources/camel-salesforce
* includes - List of SObject types to include
* excludes - List of SObject types to exclude
* includePattern - Java RegEx for SObject types to include
* excludePattern - Java RegEx for SObject types to exclude
* packageName - Java package name for generated DTOs, defaults to org.apache.camel.salesforce.dto.

Fro obvious security reasons it is recommended that the clientId, clientSecret, userName and password fields be not set in the pom.xml. 
The plugin should be configured for the rest of the properties, and can be executed using the following command:

	mvn camel-salesforce:generate -DclientId=<clientid> -DclientSecret=<clientsecret> -DuserName=<username> -Dpassword=<password>

The generated DTOs use Jackson and XStream annotations. All Salesforce field types are supported. Date and time fields are mapped to Joda DateTime, and picklist fields are mapped to generated Java Enumerations. 
