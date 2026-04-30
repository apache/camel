<#--

    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>[=GroupId]</groupId>
    <artifactId>[=ArtifactId]</artifactId>
    <version>[=Version]</version>

    <properties>
        <java.version>[=JavaVersion]</java.version>
        <project.build.outputTimestamp>[=ProjectBuildOutputTimestamp]</project.build.outputTimestamp>
[#if BuildProperties?has_content]
[=BuildProperties]
[/#if]
[#list KubernetesProperties as prop]
        <[=prop.key]>[=prop.value]</[=prop.key]>
[/#list]
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Camel BOM -->
            <dependency>
                <groupId>org.apache.camel</groupId>
                <artifactId>camel-bom</artifactId>
                <version>[=CamelVersion]</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

[#if Repositories?has_content]
    <repositories>
[#list Repositories as repo]
        <repository>
            <id>[=repo.id]</id>
            <url>[=repo.url]</url>
[#if repo.isSnapshot]
            <releases>
                <enabled>false</enabled>
            </releases>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
[/#if]
        </repository>
[/#list]
    </repositories>
    <pluginRepositories>
[#list Repositories as repo]
        <pluginRepository>
            <id>plugin-[=repo.id]</id>
            <url>[=repo.url]</url>
[#if repo.isSnapshot]
            <releases>
                <enabled>false</enabled>
            </releases>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
[/#if]
        </pluginRepository>
[/#list]
    </pluginRepositories>
[/#if]

    <dependencies>

        <dependency>
            <groupId>org.apache.camel</groupId>
            <artifactId>camel-main</artifactId>
        </dependency>
[#list Dependencies as dep]
        <dependency>
            <groupId>[=dep.groupId]</groupId>
            <artifactId>[=dep.artifactId]</artifactId>
[#if dep.version??]
            <version>[=dep.version]</version>
[/#if]
[#if dep.isLib]
            <scope>system</scope>
            <systemPath>${project.basedir}/lib/[=dep.artifactId]-[=dep.version].jar</systemPath>
[#elseif dep.scope??]
            <scope>[=dep.scope]</scope>
[/#if]
[#if dep.isKameletsUtils]
            <exclusions>
                <exclusion>
                    <groupId>org.apache.camel</groupId>
                    <artifactId>*</artifactId>
                </exclusion>
            </exclusions>
[/#if]
        </dependency>
[/#list]

        <!-- for logging in color -->
        <dependency>
            <groupId>org.fusesource.jansi</groupId>
            <artifactId>jansi</artifactId>
            <version>2.4.2</version>
        </dependency>
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-api</artifactId>
            <version>2.24.3</version>
        </dependency>
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-slf4j2-impl</artifactId>
            <version>2.24.3</version>
        </dependency>
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-core</artifactId>
            <version>2.24.3</version>
        </dependency>

        <dependency>
            <groupId>org.apache.camel</groupId>
            <artifactId>camel-test-junit6</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.15.0</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                </configuration>
            </plugin>
            <!-- mvn camel:run or mvn camel:dev -->
            <plugin>
                <groupId>org.apache.camel</groupId>
                <artifactId>camel-maven-plugin</artifactId>
                <version>[=CamelVersion]</version>
                <configuration>
                    <mainClass>[=MainClassname]</mainClass>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>prepare-fatjar</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <!-- package as runner jar -->
            <plugin>
                <groupId>org.apache.camel</groupId>
                <artifactId>camel-repackager-maven-plugin</artifactId>
                <version>[=CamelVersion]</version>
                <executions>
                    <execution>
                        <id>repackage-executable</id>
                        <phase>package</phase>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                        <configuration>
                            <mainClass>[=MainClassname]</mainClass>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
[#if hasJib]
            <plugin>
                <groupId>com.google.cloud.tools</groupId>
                <artifactId>jib-maven-plugin</artifactId>
                <version>[=JibMavenPluginVersion]</version>
                <configuration>
                    <from>
                        <image>${jib.from.image}</image>
[#if hasJibFromAuth]
                        <auth>
                            <username>${jib.from.auth.username}</username>
                            <password>${jib.from.auth.password}</password>
                        </auth>
[/#if]
                    </from>
                    <to>
                        <image>${jib.to.image}</image>
[#if hasJibToAuth]
                        <auth>
                            <username>${jib.to.auth.username}</username>
                            <password>${jib.to.auth.password}</password>
                        </auth>
[/#if]
                    </to>
                    <containerizingMode>packaged</containerizingMode>
                    <container>
                        <ports>
                            <port>[=Port]</port>
                        </ports>
                    </container>
                </configuration>
            </plugin>
[/#if]
[#if hasJkube]
            <plugin>
                <groupId>org.eclipse.jkube</groupId>
                <artifactId>kubernetes-maven-plugin</artifactId>
                <version>[=JkubeMavenPluginVersion]</version>
                <configuration>
                    <images>
                        <image>
                            <name>${jib.to.image}</name>
                            <build>
                            </build>
                        </image>
                    </images>
                    <resources>
                        <labels>
                            <all>
                                <property>
                                    <name>${label.runtime}</name>
                                    <value>camel</value>
                                </property>
                            </all>
                        </labels>
                    </resources>
                </configuration>
            </plugin>
[/#if]
        </plugins>
    </build>

    <profiles>
        <profile>
            <id>camel.debug</id>
            <activation>
                <property>
                    <name>camel.debug</name>
                    <value>true</value>
                </property>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>org.apache.camel</groupId>
                    <artifactId>camel-debug</artifactId>
                </dependency>
            </dependencies>
        </profile>
    </profiles>

</project>
