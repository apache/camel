<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<artifactId>${name}</artifactId>
	<groupId>${groupId}</groupId>
	<version>1.0.0-SNAPSHOT</version>

	<name>${name}</name>

	<properties>
		<#list pomProperties as property>
        <${property.key}>${property.value}</${property.key}>
        </#list>
		<camel-quarkus.platform.version>${r"${quarkus.platform.version}"}</camel-quarkus.platform.version>

		<quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
		<quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
		<camel-quarkus.platform.group-id>${r"${quarkus.platform.group-id}"}</camel-quarkus.platform.group-id>
		<camel-quarkus.platform.artifact-id>quarkus-camel-bom</camel-quarkus.platform.artifact-id>

		<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
		<project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
		<maven.compiler.target>11</maven.compiler.target>
		<maven.compiler.source>11</maven.compiler.source>
		<maven.compiler.testTarget>${r"${maven.compiler.target}"}</maven.compiler.testTarget>
		<maven.compiler.testSource>${r"${maven.compiler.source}"}</maven.compiler.testSource>

		<formatter-maven-plugin.version>2.11.0</formatter-maven-plugin.version>
		<impsort-maven-plugin.version>1.3.2</impsort-maven-plugin.version>
		<maven-compiler-plugin.version>3.8.0</maven-compiler-plugin.version>
		<maven-jar-plugin.version>3.2.0</maven-jar-plugin.version>
		<maven-resources-plugin.version>3.1.0</maven-resources-plugin.version>
		<maven-surefire-plugin.version>2.22.2</maven-surefire-plugin.version>
		<mycila-license.version>3.0</mycila-license.version>
	</properties>

	<dependencyManagement>
		<dependencies>
			<!-- Import BOM -->
			<dependency>
				<groupId>${r"${quarkus.platform.group-id}"}</groupId>
				<artifactId>${r"${quarkus.platform.artifact-id}"}</artifactId>
				<version>${r"${quarkus.platform.version}"}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
			<dependency>
				<groupId>${r"${camel-quarkus.platform.group-id}"}</groupId>
				<artifactId>${r"${camel-quarkus.platform.artifact-id}"}</artifactId>
				<version>${r"${camel-quarkus.platform.version}"}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>

	<dependencies>
		<#list pomDependencies as dependency>
		<dependency>
			<groupId>${dependency.groupId}</groupId>
			<artifactId>${dependency.artifactId}</artifactId>
			<#if dependency.version??>
			<version>${dependency.version}</version>
            </#if>
		</dependency>
		</#list>

		<!-- Test -->
		<dependency>
			<groupId>io.quarkus</groupId>
			<artifactId>quarkus-junit5</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.awaitility</groupId>
			<artifactId>awaitility</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<pluginManagement>
			<plugins>

				<plugin>
					<groupId>net.revelc.code.formatter</groupId>
					<artifactId>formatter-maven-plugin</artifactId>
					<version>${r"${formatter-maven-plugin.version}"}</version>
				</plugin>

				<plugin>
					<groupId>net.revelc.code</groupId>
					<artifactId>impsort-maven-plugin</artifactId>
					<version>${r"${impsort-maven-plugin.version}"}</version>
					<configuration>
						<groups>java.,javax.,org.w3c.,org.xml.,junit.</groups>
						<removeUnused>true</removeUnused>
						<staticAfter>true</staticAfter>
						<staticGroups>java.,javax.,org.w3c.,org.xml.,junit.</staticGroups>
					</configuration>
				</plugin>

				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-compiler-plugin</artifactId>
					<version>${r"${maven-compiler-plugin.version}"}</version>
					<configuration>
						<showDeprecation>true</showDeprecation>
						<showWarnings>true</showWarnings>
						<compilerArgs>
							<arg>-Xlint:unchecked</arg>
						</compilerArgs>
					</configuration>
				</plugin>

				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-surefire-plugin</artifactId>
					<version>${r"${maven-surefire-plugin.version}"}</version>
					<configuration>
						<failIfNoTests>false</failIfNoTests>
						<systemProperties>
							<java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
						</systemProperties>
					</configuration>
				</plugin>

				<plugin>
					<groupId>${r"${quarkus.platform.group-id}"}</groupId>
					<artifactId>quarkus-maven-plugin</artifactId>
					<version>${r"${quarkus.platform.version}"}</version>
				</plugin>

				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-failsafe-plugin</artifactId>
					<version>${r"${maven-surefire-plugin.version}"}</version>
				</plugin>

				<plugin>
					<groupId>org.apache.maven.plugins</groupId>
					<artifactId>maven-jar-plugin</artifactId>
					<version>${r"${maven-jar-plugin.version}"}</version>
				</plugin>

				<plugin>
					<groupId>com.mycila</groupId>
					<artifactId>license-maven-plugin</artifactId>
					<version>${r"${mycila-license.version}"}</version>
					<configuration>
						<failIfUnknown>true</failIfUnknown>
						<header>${r"${maven.multiModuleProjectDirectory}"}/header.txt</header>
						<excludes>
							<exclude>**/*.adoc</exclude>
							<exclude>**/*.txt</exclude>
							<exclude>**/LICENSE.txt</exclude>
							<exclude>**/LICENSE</exclude>
							<exclude>**/NOTICE.txt</exclude>
							<exclude>**/NOTICE</exclude>
							<exclude>**/README</exclude>
							<exclude>**/pom.xml.versionsBackup</exclude>
						</excludes>
						<mapping>
							<java>SLASHSTAR_STYLE</java>
							<properties>CAMEL_PROPERTIES_STYLE</properties>
							<kt>SLASHSTAR_STYLE</kt>
						</mapping>
						<headerDefinitions>
							<headerDefinition>${r"${maven.multiModuleProjectDirectory}"}/license-properties-headerdefinition.xml</headerDefinition>
						</headerDefinitions>
					</configuration>
				</plugin>
			</plugins>
		</pluginManagement>

		<plugins>
			<plugin>
				<groupId>${r"${quarkus.platform.group-id}"}</groupId>
				<artifactId>quarkus-maven-plugin</artifactId>
				<executions>
					<execution>
						<id>build</id>
						<goals>
							<goal>build</goal>
						</goals>
					</execution>
				</executions>
				<configuration>
					<workingDir>${r"${project.basedir}"}</workingDir>
				</configuration>
			</plugin>

			<plugin>
				<groupId>net.revelc.code.formatter</groupId>
				<artifactId>formatter-maven-plugin</artifactId>
				<executions>
					<execution>
						<id>format</id>
						<goals>
							<goal>format</goal>
						</goals>
						<phase>process-sources</phase>
					</execution>
				</executions>
			</plugin>

			<plugin>
				<groupId>net.revelc.code</groupId>
				<artifactId>impsort-maven-plugin</artifactId>
				<executions>
					<execution>
						<id>sort-imports</id>
						<goals>
							<goal>sort</goal>
						</goals>
						<phase>process-sources</phase>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>

	<profiles>
		<profile>
			<id>native</id>
			<activation>
				<property>
					<name>native</name>
				</property>
			</activation>
			<properties>
				<quarkus.package.type>native</quarkus.package.type>
			</properties>
			<build>
				<plugins>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-failsafe-plugin</artifactId>
						<executions>
							<execution>
								<goals>
									<goal>integration-test</goal>
									<goal>verify</goal>
								</goals>
								<configuration>
									<systemPropertyVariables>
										<quarkus.package.type>${r"${quarkus.package.type}"}</quarkus.package.type>
									</systemPropertyVariables>
								</configuration>
							</execution>
						</executions>
					</plugin>
				</plugins>
			</build>
		</profile>
	</profiles>

</project>