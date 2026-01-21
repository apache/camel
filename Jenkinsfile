/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
def MAVEN_PARAMS = "-B -e -fae -V -Dnoassembly -Dmaven.compiler.fork=true -Dsurefire.rerunFailingTestsCount=2 -Dfailsafe.rerunFailingTestsCount=1"
def MAVEN_TEST_PARAMS = env.MAVEN_TEST_PARAMS ?: "-Dkafka.instance.type=local-strimzi-container -Dci.env.name=apache.org"
def MAVEN_TEST_PARAMS_UBUNTU = env.MAVEN_TEST_PARAMS ?: "-Dci.env.name=apache.org"
/*
Below parameters are required for camel/core/camel-core module's test cases to pass on ppc64 and s390x
- xpathExprGrpLimit: limits the number of groups an Xpath expression can contain
- xpathExprOpLimit: limits the number of operators an Xpath expression can contain
- unsupported-arch: won't build modules which are not supported in the given architecture
*/
def MAVEN_TEST_PARAMS_ALT_ARCHS = "-Djdk.xml.xpathExprGrpLimit=100 -Djdk.xml.xpathExprOpLimit=2000 -Dunsupported-arch"

pipeline {

    environment {
        MAVEN_SKIP_RC = true
        DEVELOCITY_ACCESS_KEY = credentials('CAMEL_DEVELOCITY_ACCESS_KEY')
    }

    options {
        buildDiscarder(
            logRotator(artifactNumToKeepStr: '5', numToKeepStr: '10')
        )
        disableConcurrentBuilds()
        throttleJobProperty(
          categories: ['camel'],
          throttleEnabled: true,
          throttleOption: 'category'
      )

        // This is required if you want to clean before build
        skipDefaultCheckout(true)
    }

    parameters {
        booleanParam(name: 'VIRTUAL_THREAD', defaultValue: false, description: 'Perform the build using virtual threads')
        choice(name: 'PLATFORM_FILTER', choices: ['all', 'ppc64le', 's390x', 'ubuntu-avx'], description: 'Run on specific platform')
        choice(name: 'JDK_FILTER', choices: ['all', 'jdk_17_latest', 'jdk_21_latest', 'jdk_25_latest'], description: 'Run on specific jdk')
    }
    agent none
    stages {
        stage('BuildAndTest') {
            matrix {
                agent {
                    label "${PLATFORM}"
                }
                options {
                    throttle(['camel'])
                }
                when { anyOf {
                    expression { params.PLATFORM_FILTER == 'all' }
                    expression { params.PLATFORM_FILTER == env.PLATFORM }
                    expression { params.JDK_FILTER == 'all' }
                    expression { params.JDK_FILTER == env.JDK_NAME }
                } }
                axes {
                    axis {
                        name 'JDK_NAME'
                        values 'jdk_17_latest', 'jdk_21_latest', 'jdk_25_latest'
                    }
                    axis {
                        name 'PLATFORM'
                        values 'ppc64le', 's390x', 'ubuntu-avx'
                    }
                }
                excludes {
                    exclude {
                        axis {
                            name 'JDK_NAME'
                            values 'jdk_21_latest'
                        }
                        axis {
                            name 'PLATFORM'
                            values 'ppc64le'
                        }
                    }
                    exclude {
                        axis {
                            name 'JDK_NAME'
                            values 'jdk_21_latest'
                        }
                        axis {
                            name 'PLATFORM'
                            values 's390x'
                        }
                    }
                    exclude {
                        axis {
                            name 'JDK_NAME'
                            values 'jdk_25_latest'
                        }
                        axis {
                            name 'PLATFORM'
                            values 'ppc64le'
                        }
                    }
                    exclude {
                        axis {
                            name 'JDK_NAME'
                            values 'jdk_25_latest'
                        }
                        axis {
                            name 'PLATFORM'
                            values 's390x'
                        }
                    }
                }
                tools {
                    jdk "${JDK_NAME}"
                }
                stages {
                    stage('Clean workspace') {
                        steps {
                            cleanWs()
                            sh 'rm -rv /home/jenkins/.m2/repository/org/apache/camel'
                            checkout scm
                        }
                    }

                    stage('Build and test') {
                        steps {
                            echo "Do Build and test for ${PLATFORM}-${JDK_NAME}"
                            sh 'java -version'
                            timeout(unit: 'HOURS', time: 7) {
                                script {
                                    if ("${PLATFORM}" == "ubuntu-avx") {
                                        if ("${JDK_NAME}" == "jdk_21_latest") {
                                            // Enable virtual threads
                                            sh "./mvnw $MAVEN_PARAMS $MAVEN_TEST_PARAMS_UBUNTU -Darchetype.test.skip -Dmaven.test.failure.ignore=true -Dcheckstyle.skip=true install -Dcamel.threads.virtual.enabled=${params.VIRTUAL_THREAD}"
                                        } else if ("${JDK_NAME}" == "jdk_17_latest") {
                                            // Enable coverage required later by Sonar check
                                            sh "./mvnw $MAVEN_PARAMS $MAVEN_TEST_PARAMS -Darchetype.test.skip -Dmaven.test.failure.ignore=true -Dcheckstyle.skip=true install -Pcoverage"
                                        } else {
                                            sh "./mvnw $MAVEN_PARAMS $MAVEN_TEST_PARAMS -Darchetype.test.skip -Dmaven.test.failure.ignore=true -Dcheckstyle.skip=true install"
                                        }
                                    } else {
                                        // Skip the test case execution of modules which are either not supported on ppc64le or vendor images are not available for ppc64le.
                                        sh "./mvnw $MAVEN_PARAMS $MAVEN_TEST_PARAMS $MAVEN_TEST_PARAMS_ALT_ARCHS -Darchetype.test.skip -Dmaven.test.failure.ignore=true -Dcheckstyle.skip=true install -pl '!docs'"
                                        echo "Code quality review disabled for ${PLATFORM} with JDK ${JDK_NAME}"
                                    }
                                }
                            }
                        }
                        post {
                            always {
                                junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml', skipPublishingChecks: true
                                junit allowEmptyResults: true, testResults: '**/target/failsafe-reports/*.xml', skipPublishingChecks: true
                            }
                        }
                    }

                    stage('Static code analysis') {
                        steps {
                            script {
                                echo "Do Static code analysis for ${PLATFORM}-${JDK_NAME}"
                                // We only execute this on the main PLATFORM/JDK target
                                if ("${PLATFORM}" == "ubuntu-avx" && "${JDK_NAME}" == "jdk_17_latest") {
                                    withCredentials([string(credentialsId: 'apache-camel-core', variable: 'SONAR_TOKEN')]) {
                                        echo "Code quality review ENABLED for ${PLATFORM} with ${JDK_NAME}"
                                        sh "./mvnw $MAVEN_PARAMS $MAVEN_TEST_PARAMS -Dsonar.host.url=https://sonarcloud.io -Dsonar.java.experimental.batchModeSizeInKB=2048 -Dsonar.organization=apache -Dsonar.projectKey=apache_camel -Dsonar.branch.name=$BRANCH_NAME org.sonarsource.scanner.maven:sonar-maven-plugin:sonar"
                                    }
                                } else {
                                    echo "Code quality review DISABLED for ${PLATFORM} with ${JDK_NAME}"
                                }
                            }
                        }
                    }
                }
                post {
                    always {
                        echo "Sending report CI email for developers"
                        emailext(
                            subject: '${DEFAULT_SUBJECT}',
                            body: '${DEFAULT_CONTENT}',
                            recipientProviders: [[$class: 'DevelopersRecipientProvider']]
                        )
                    }
                }
            }
        }
    }
}
