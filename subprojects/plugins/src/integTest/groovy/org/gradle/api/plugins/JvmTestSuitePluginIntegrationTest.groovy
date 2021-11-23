/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.TestFile

class JvmTestSuitePluginIntegrationTest extends AbstractIntegrationSpec {

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "JVM Test Suites plugin adds outgoing variants for default test suite"() {
        settingsFile << "rootProject.name = 'Test'"

        buildFile << """
            plugins {
                id 'jvm-test-suite'
                id 'java'
            }
            """

        file("src/test/java/SomeTest.java") << """
            import org.junit.Test;

            public class SomeTest {
                @Test public void foo() {}
            }
            """.stripIndent()

        expect:
        succeeds "outgoingVariants"

        def resultsBinPath = new TestFile(getTestDirectory(), 'build/test-results/test/binary/results.bin').getRelativePathFromBase()
        outputContains("""
            --------------------------------------------------
            Variant testResultsElementsForTest
            --------------------------------------------------
            Capabilities
                - :Test:unspecified (default capability)
            Attributes
                - org.gradle.category              = verification
                - org.gradle.testsuite.name        = test
                - org.gradle.testsuite.target.name = test
                - org.gradle.testsuite.type        = unit-tests
                - org.gradle.verificationtype      = test-results

            Artifacts
                - $resultsBinPath (artifactType = binary)
            """.stripIndent())
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "JVM Test Suites plugin adds outgoing variants for custom test suite"() {
        settingsFile << "rootProject.name = 'Test'"

        buildFile << """
            plugins {
                id 'jvm-test-suite'
                id 'java'
            }

            testing {
                suites {
                    integrationTest(JvmTestSuite) {
                        testType = TestSuiteType.INTEGRATION_TESTS

                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        expect:
        succeeds "outgoingVariants"

        def resultsBinPath = new TestFile(getTestDirectory(), 'build/test-results/integrationTest/binary/results.bin').getRelativePathFromBase()
        outputContains("""
            --------------------------------------------------
            Variant testResultsElementsForIntegrationTest
            --------------------------------------------------
            Capabilities
                - :Test:unspecified (default capability)
            Attributes
                - org.gradle.category              = verification
                - org.gradle.testsuite.name        = integrationTest
                - org.gradle.testsuite.target.name = integrationTest
                - org.gradle.testsuite.type        = integration-tests
                - org.gradle.verificationtype      = test-results

            Artifacts
                - $resultsBinPath (artifactType = binary)
            """.stripIndent())
    }

    def "Test coverage data can be consumed by another task via Dependency Management"() {
        buildFile << """
            plugins {
                id 'jvm-test-suite'
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit()
                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        file("src/test/java/SomeTest.java") << """
            import org.junit.Test;

            public class SomeTest {
                @Test public void foo() {}
            }
            """.stripIndent()

        buildFile << """
            // A resolvable configuration to collect test results data
            def testDataConfig = configurations.create("testData") {
                visible = false
                canBeResolved = true
                canBeConsumed = false
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.VERIFICATION))
                    attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType, VerificationType.TEST_RESULTS))
                }
            }

            dependencies {
                testData project
            }

            def testResolve = tasks.register('testResolve') {
                doLast {
                    assert testDataConfig.getResolvedConfiguration().getFiles()*.getName() == [test.binaryResultsDirectory.file("results.bin").get().getAsFile().getName()]
                }
            }""".stripIndent()

        expect:
        succeeds('testResolve')
    }

    def "Test results data can be consumed by another task in a different project via Dependency Management"() {
        def subADir = createDir("subA")
        subADir.file("build.gradle") << """
            plugins {
                id 'jvm-test-suite'
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit()
                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        subADir.file("src/test/java/SomeTestA.java") << """
            import org.junit.Test;

            public class SomeTestA {
                @Test public void foo() {}
            }
            """.stripIndent()

        def subBDir = createDir("subB")
        subBDir.file("build.gradle") << """
            plugins {
                id 'jvm-test-suite'
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit()
                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        subBDir.file("src/test/java/SomeTestB.java") << """
            import org.junit.Test;

            public class SomeTestB {
                @Test public void foo() {}
            }
            """.stripIndent()

        settingsFile << """
            include ':subA'
            include ':subB'
            """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
            }

            dependencies {
                implementation project(':subA')
                implementation project(':subB')
            }

            // A resolvable configuration to collect test results data
            def testDataConfig = configurations.create("testData") {
                visible = false
                canBeResolved = true
                canBeConsumed = false
                extendsFrom(configurations.implementation)
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.VERIFICATION))
                    attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType, VerificationType.TEST_RESULTS))
                }
            }

            def testResolve = tasks.register('testResolve') {
                def expectedResultsFiles = [file("subA/build/test-results/test/binary/results.bin"),
                                            file("subB/build/test-results/test/binary/results.bin")]
                doLast {
                    assert testDataConfig.getResolvedConfiguration().getFiles().containsAll(expectedResultsFiles)
                }
            }

            """
        expect:
        succeeds('testResolve')
    }

    @ToBeFixedForConfigurationCache(because = "task references another task")
    def "Test results data can be consumed across transitive project dependencies via Dependency Management"() {
        def subDirectDir = createDir("direct")
        subDirectDir.file("build.gradle") << """
            plugins {
                id 'jvm-test-suite'
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            dependencies {
                implementation project(':transitive')
            }

            testing {
                suites {
                    test {
                        useJUnit()
                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        subDirectDir.file("src/test/java/SomeTestD.java") << """
            import org.junit.Test;

            public class SomeTestD {
                @Test public void foo() {}
            }
            """.stripIndent()

        def subTransitiveDir = createDir("transitive")
        subTransitiveDir.file("build.gradle") << """
            plugins {
                id 'jvm-test-suite'
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            testing {
                suites {
                    test {
                        useJUnit()
                        dependencies {
                            implementation project
                        }
                    }
                }
            }
            """.stripIndent()

        subTransitiveDir.file("src/test/java/SomeTestT.java") << """
            import org.junit.Test;

            public class SomeTestT {
                @Test public void foo() {}
            }
            """.stripIndent()

        settingsFile << """
            include ':direct'
            include ':transitive'
            """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
            }

            dependencies {
                implementation project(':direct')
            }

            // A resolvable configuration to collect test results data
            def testDataConfig = configurations.create("testData") {
                visible = false
                canBeResolved = true
                canBeConsumed = false
                extendsFrom(configurations.implementation)
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.VERIFICATION))
                    attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType, VerificationType.TEST_RESULTS))
                }
            }

            def testResolve = tasks.register('testResolve') {
                doLast {
                    assert testDataConfig.getResolvedConfiguration().getFiles().containsAll([project(':direct').tasks["test"].binaryResultsDirectory.file("results.bin").get().getAsFile(),
                                                                                             project(':transitive').tasks["test"].binaryResultsDirectory.file("results.bin").get().getAsFile()])
                }
            }

            """
        expect:
        succeeds('testResolve')
    }

    private String systemFilePath(String path) {
        return path.replace('/', File.separator)
    }
}
