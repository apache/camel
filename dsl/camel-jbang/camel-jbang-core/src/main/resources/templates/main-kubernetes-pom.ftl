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
[#if BuildProperties?has_content]
[=BuildProperties]
[/#if]
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
            <plugin>
                <groupId>org.eclipse.jkube</groupId>
                <artifactId>${jkube.maven.plugin}</artifactId>
                <version>${jkube.version}</version>
                <configuration>
                    <buildStrategy>${jkube.build.strategy}</buildStrategy>
                    <images>
                        <image>
                            <build>
                                <from>${jkube.container-image.from}</from>
                                <entryPoint>
                                    <exec>
                                        <arg>/maven/run-java.sh</arg>
                                        <arg>run</arg>
                                    </exec>
                                </entryPoint>
                                <assembly>
                                    <layers>
                                        <layer>
                                            <id>entrypoint</id>
                                            <files>
                                                <file>
                                                    <source>src/main/scripts/run-java.sh</source>
                                                    <outputDirectory>.</outputDirectory>
                                                    <fileMode>755</fileMode>
                                                </file>
                                            </files>
                                        </layer>
                                        <layer>
                                            <id>tls</id>
                                            <fileSets>
                                                <fileSet>
                                                    <directory>src/main/tls</directory>
                                                    <outputDirectory>./tls</outputDirectory>
                                                </fileSet>
                                            </fileSets>
                                        </layer>
                                    </layers>
                                </assembly>
                            </build>
                        </image>
                    </images>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>build</goal>
                            <goal>resource</goal>
                            <goal>push</goal>
                        </goals>
                        <phase>package</phase>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-resources-plugin</artifactId>
                <version>3.4.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>copy-resources</goal>
                        </goals>
                        <configuration>
                            <!-- Copy kubernetes resources to target/kubernetes -->
                            <outputDirectory>${project.build.directory}/kubernetes</outputDirectory>
                            <resources>
                                <resource>
                                    <directory>${project.build.directory}/classes/META-INF/jkube</directory>
                                </resource>
                            </resources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
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
