/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

def BUILD_WEBSITE = !'false'.equalsIgnoreCase(env.BUILD_WEBSITE)
def LOCAL_REPOSITORY = env.LOCAL_REPOSITORY ?: '/home/jenkins/jenkins-slave/maven-repositories/0'
def AGENT_LABEL = env.AGENT_LABEL ?: 'ubuntu'
def WEBSITE_AGENT_LABEL = env.WEBSITE_AGENT_LABEL ?: 'git-websites'
def JDK_NAME = env.JDK_NAME ?: 'JDK 1.8 (latest)'

def MAVEN_PARAMS = "-U -B -e -fae -V -Dmaven.repo.local=${LOCAL_REPOSITORY} -Dnoassembly -Dmaven.compiler.fork=true -Dsurefire.rerunFailingTestsCount=2"

pipeline {

    agent {
        label AGENT_LABEL
    }

    tools {
        jdk JDK_NAME
    }

    options {
        buildDiscarder(
            logRotator(artifactNumToKeepStr: '5', numToKeepStr: '10')
        )
        disableConcurrentBuilds()
    }

    stages {

        stage('Website') {
            when {
                beforeAgent true
                expression { BUILD_WEBSITE }
                anyOf {
                    changeset '**/*.adoc'
                    changeset 'camel-website/**'
                }
            }

            agent {
                node {
                    label WEBSITE_AGENT_LABEL 
                }
            }

            options {
                skipDefaultCheckout()
            }

            steps {
                dir('camel') {
                    checkout scm
                    sh "./mvnw -U -B -V -f camel-website"
                }

                dir('site') {
/*                    checkout [ branches: [name: 'asf-site'] ]
                    sh "cp -R ../camel/camel-website/public/* ."
                    sh "git add ."*/
                }
            }
        }

        stage('Build & Deploy') {

            when {
                beforeAgent true
                branch 'master'
                not {
                    anyOf {
                        changeset '**/*.adoc'
                        changeset 'camel-website/**'
                    }
                }
            }

            steps {
                sh "./mvnw $MAVEN_PARAMS -Dmaven.test.skip.exec=true clean deploy"
            }
        }

        stage('Build') {
            when {
                beforeAgent true
                not {
                    allOf {
                        branch 'master'
                        anyOf {
                            changeset '**/*.adoc'
                            changeset 'camel-website/**'
                        }
                    }
                }
            }

            steps {
                sh "./mvnw $MAVEN_PARAMS -Dmaven.test.skip.exec=true clean install"
            }
        }

        stage('Checks') {
            when {
                beforeAgent true
                not {
                    anyOf {
                        changeset '**/*.adoc'
                        changeset 'camel-website/**'
                    }
                }
            }

            steps {
                sh "./mvnw $MAVEN_PARAMS -Psourcecheck -Dcheckstyle.failOnViolation=false checkstyle:check"
            }
            post {
                always {
                    checkstyle pattern: '**/checkstyle-result.xml', canRunOnFailed: true
                }
            }
        }

        stage('Test') {
            when {
                beforeAgent true
                not {
                    anyOf {
                        changeset '**/*.adoc'
                        changeset 'camel-website/**'
                    }
                }
            }

            steps {
                sh "./mvnw $MAVEN_PARAMS -Dmaven.test.failure.ignore=true test"
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                    junit allowEmptyResults: true, testResults: '**/target/failsafe-reports/*.xml'
                }
            }
        }

    }

    post {
        always {
            emailext(
                subject: '${DEFAULT_SUBJECT}',
                body: '${DEFAULT_CONTENT}',
                recipientProviders: [[$class: 'CulpritsRecipientProvider']]
            )
        }
    }
}

