package com.novoda.staticanalysis.internal.spotbugs

import com.google.common.truth.Truth
import com.novoda.test.TestProject
import com.novoda.test.TestProjectRule
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.novoda.test.Fixtures.Findbugs.*
import static com.novoda.test.LogsSubject.assertThat
import static com.novoda.test.TestProjectSubject.assumeThat

@RunWith(Parameterized.class)
class SpotBugsIntegrationTest {

    @Parameterized.Parameters(name = "{0}")
    static Iterable<TestProjectRule> rules() {
        return [TestProjectRule.forJavaProject(), TestProjectRule.forAndroidProject()]
    }

    @Rule
    public final TestProjectRule projectRule

    SpotBugsIntegrationTest(TestProjectRule projectRule) {
        this.projectRule = projectRule
    }

    @Test
    void shouldFailBuildWhenSpotBugsWarningsOverTheThreshold() {
        TestProject.Result result = projectRule.newProject()
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION, SOURCES_WITH_MEDIUM_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 1
                }''')
                .withToolsConfig('spotbugs {}')
                .buildAndFail('check')

        assertThat(result.logs).containsLimitExceeded(0, 1)
        assertThat(result.logs).containsSpotBugsViolations(0, 2,
                result.buildFileUrl('reports/spotbugs/debug.html'))
    }

    @Test
    void shouldFailBuildAfterSecondRunWhenSpotBugsWarningsStillOverTheThreshold() {
        def project = projectRule.newProject()
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION, SOURCES_WITH_MEDIUM_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 1
                }''')
                .withToolsConfig('spotbugs {}')

        TestProject.Result result = project.buildAndFail('check')

        assertThat(result.logs).containsLimitExceeded(0, 1)
        assertThat(result.logs).containsSpotBugsViolations(0, 2,
                result.buildFileUrl('reports/spotbugs/debug.html'))

        result = project.buildAndFail('check')

        assertThat(result.logs).containsLimitExceeded(0, 1)
        assertThat(result.logs).containsSpotBugsViolations(0, 2,
                result.buildFileUrl('reports/spotbugs/debug.html'))
    }

    @Test
    void shouldDetectMoreWarningsWhenEffortIsMaxAndReportLevelIsLow() {
        TestProject.Result result = projectRule.newProject()
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION, SOURCES_WITH_MEDIUM_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 1
                }''')
                .withToolsConfig("spotbugs { effort = 'max' \n reportLevel = 'low'}")
                .buildAndFail('check')

        assertThat(result.logs).containsLimitExceeded(0, 2)
        assertThat(result.logs).containsSpotBugsViolations(0, 3,
                result.buildFileUrl('reports/spotbugs/debug.html'))
    }

    @Test
    void shouldFailBuildWhenSpotBugsErrorsOverTheThreshold() {
        TestProject.Result result = projectRule.newProject()
                .withSourceSet('debug', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 0
                }''')
                .withToolsConfig('spotbugs {}')
                .buildAndFail('check')

        assertThat(result.logs).containsLimitExceeded(1, 0)
        assertThat(result.logs).containsSpotBugsViolations(1, 0,
                result.buildFileUrl('reports/spotbugs/debug.html'))
    }

    @Test
    void shouldNotFailBuildWhenNoSpotBugsWarningsOrErrorsEncounteredAndNoThresholdTrespassed() {
        TestProject.Result result = projectRule.newProject()
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 0
                }''')
                .withToolsConfig('spotbugs {}')
                .build('check')

        assertThat(result.logs).doesNotContainLimitExceeded()
        assertThat(result.logs).doesNotContainSpotBugsViolations()
    }

    @Test
    void shouldNotFailBuildWhenSpotBugsWarningsAndErrorsEncounteredAndNoThresholdTrespassed() {
        TestProject.Result result = projectRule.newProject()
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION, SOURCES_WITH_MEDIUM_VIOLATION)
                .withSourceSet('release', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 10
                    maxWarnings = 10
                }''')
                .withToolsConfig('spotbugs {}')
                .build('check')

        assertThat(result.logs).doesNotContainLimitExceeded()
        assertThat(result.logs).containsSpotBugsViolations(1, 2,
                result.buildFileUrl('reports/spotbugs/debug.html'),
                result.buildFileUrl('reports/spotbugs/release.html'))
    }

    @Test
    void shouldNotFailBuildWhenSpotBugsConfiguredToNotIgnoreFailures() {
        projectRule.newProject()
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION, SOURCES_WITH_MEDIUM_VIOLATION)
                .withSourceSet('release', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 10
                    maxWarnings = 10
                }''')
                .withToolsConfig('spotbugs { ignoreFailures = false }')
                .build('check')
    }

    @Test
    void shouldNotFailBuildWhenSpotBugsNotConfigured() {
        projectRule.newProject()
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION, SOURCES_WITH_MEDIUM_VIOLATION)
                .withSourceSet('release', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 0
                }''')
                .build('check')
    }

    @Test
    void shouldNotFailBuildWhenSpotBugsConfiguredToExcludePattern() {
        TestProject.Result result = projectRule.newProject()
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION)
                .withSourceSet('release', SOURCES_WITH_HIGH_VIOLATION, SOURCES_WITH_MEDIUM_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 10
                }''')
                .withToolsConfig('spotbugs { exclude "com/novoda/test/HighPriorityViolator.java" }')
                .build('check')

        assertThat(result.logs).doesNotContainLimitExceeded()
        assertThat(result.logs).containsSpotBugsViolations(0, 2,
                result.buildFileUrl('reports/spotbugs/debug.html'))
    }

    @Test
    void shouldNotFailBuildWhenSpotBugsConfiguredToIgnoreFaultySourceFolder() {
        TestProject.Result result = projectRule.newProject()
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION, SOURCES_WITH_MEDIUM_VIOLATION)
                .withSourceSet('release', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 10
                }''')
                .withToolsConfig("spotbugs { exclude project.fileTree('${SOURCES_WITH_HIGH_VIOLATION}') }")
                .build('check')

        assertThat(result.logs).doesNotContainLimitExceeded()
        assertThat(result.logs).containsSpotBugsViolations(0, 2,
                result.buildFileUrl('reports/spotbugs/debug.html'))
    }

    @Test
    void shouldNotFailBuildWhenSpotBugsConfiguredToIgnoreFaultyJavaSourceSets() {
        TestProject project = projectRule.newProject()
        assumeThat(project).isJavaProject()

        TestProject.Result result = project
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION, SOURCES_WITH_MEDIUM_VIOLATION)
                .withSourceSet('test', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 10
                }''')
                .withToolsConfig('spotbugs { exclude project.sourceSets.test.java.srcDirs }')
                .build('check')

        assertThat(result.logs).doesNotContainLimitExceeded()
        assertThat(result.logs).containsSpotBugsViolations(0, 2,
                result.buildFileUrl('reports/spotbugs/debug.html'))
    }

    @Test
    void shouldNotFailBuildWhenSpotBugsConfiguredToIgnoreFaultyAndroidSourceSets() {
        TestProject project = projectRule.newProject()
        assumeThat(project).isAndroidProject()

        TestProject.Result result = project
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION)
                .withSourceSet('test', SOURCES_WITH_HIGH_VIOLATION)
                .withSourceSet('androidTest', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 1
                }''')
                .withToolsConfig('''spotbugs {
                    exclude project.android.sourceSets.test.java.srcDirs
                    exclude project.android.sourceSets.androidTest.java.srcDirs
                }''')
                .build('check')

        assertThat(result.logs).doesNotContainLimitExceeded()
        assertThat(result.logs).containsSpotBugsViolations(0, 1,
                result.buildFileUrl('reports/spotbugs/debug.html'))
    }

    @Test
    void shouldCollectDuplicatedSpotBugsWarningsAndErrorsAcrossAndroidVariantsForSharedSourceSets() {
        TestProject project = projectRule.newProject()
        assumeThat(project).isAndroidProject()

        TestProject.Result result = project
                .withSourceSet('main', SOURCES_WITH_LOW_VIOLATION, SOURCES_WITH_MEDIUM_VIOLATION, SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 10
                    maxWarnings = 10
                }''')
                .withToolsConfig('spotbugs {}')
                .build('check')

        assertThat(result.logs).doesNotContainLimitExceeded()
        assertThat(result.logs).containsSpotBugsViolations(2, 4,
                result.buildFileUrl('reports/spotbugs/debug.html'),
                result.buildFileUrl('reports/spotbugs/release.html'))
    }

    @Test
    void shouldSkipSpotBugsTasksForIgnoredFaultyJavaSourceSets() {
        TestProject project = projectRule.newProject()
        assumeThat(project).isJavaProject()

        TestProject.Result result = project
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION, SOURCES_WITH_MEDIUM_VIOLATION)
                .withSourceSet('test', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 10
                }''')
                .withToolsConfig('spotbugs { exclude project.sourceSets.test.java.srcDirs }')
                .build('check')

        Truth.assertThat(result.outcome(':spotbugsDebug')).isEqualTo(TaskOutcome.SUCCESS)
        Truth.assertThat(result.outcome(':generateSpotbugsDebugHtmlReport')).isEqualTo(TaskOutcome.SUCCESS)
        Truth.assertThat(result.outcome(':spotbugsTest')).isEqualTo(TaskOutcome.NO_SOURCE)
        Truth.assertThat(result.outcome(':generateSpotbugsTestHtmlReport')).isEqualTo(TaskOutcome.SKIPPED)
    }

    @Test
    void shouldSkipSpotBugsTasksForIgnoredFaultyAndroidSourceSets() {
        TestProject project = projectRule.newProject()
        assumeThat(project).isAndroidProject()

        TestProject.Result result = project
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION)
                .withSourceSet('test', SOURCES_WITH_HIGH_VIOLATION)
                .withSourceSet('androidTest', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 1
                }''')
                .withToolsConfig('''spotbugs {
                    exclude project.android.sourceSets.test.java.srcDirs
                    exclude project.android.sourceSets.androidTest.java.srcDirs
                }''')
                .build('check')

        Truth.assertThat(result.outcome(':spotbugsDebugAndroidTest')).isEqualTo(TaskOutcome.NO_SOURCE)
        Truth.assertThat(result.outcome(':generateSpotbugsDebugAndroidTestHtmlReport')).isEqualTo(TaskOutcome.SKIPPED)
        Truth.assertThat(result.outcome(':spotbugsDebug')).isEqualTo(TaskOutcome.SUCCESS)
        Truth.assertThat(result.outcome(':generateSpotbugsDebugHtmlReport')).isEqualTo(TaskOutcome.SUCCESS)
        Truth.assertThat(result.outcome(':spotbugsDebugUnitTest')).isEqualTo(TaskOutcome.NO_SOURCE)
        Truth.assertThat(result.outcome(':generateSpotbugsDebugUnitTestHtmlReport')).isEqualTo(TaskOutcome.SKIPPED)
        Truth.assertThat(result.outcome(':spotbugsRelease')).isEqualTo(TaskOutcome.NO_SOURCE)
        Truth.assertThat(result.outcome(':generateSpotbugsReleaseHtmlReport')).isEqualTo(TaskOutcome.SKIPPED)
    }

    @Test
    void shouldProvideNoClassesToSpotBugsTaskWhenNoJavaSourcesToAnalyse() {
        TestProject project = projectRule.newProject()
        assumeThat(project).isJavaProject()

        TestProject.Result result = project
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION, SOURCES_WITH_MEDIUM_VIOLATION)
                .withSourceSet('test', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 10
                }''')
                .withToolsConfig('spotbugs { exclude project.sourceSets.test.java.srcDirs }')
                .withAdditionalConfiguration(addCheckSpotbugsClassesTask())
                .build('checkSpotbugsClasses')

        Truth.assertThat(result.outcome(':checkSpotbugsClasses')).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    void shouldProvideNoClassesToSpotBugsTaskWhenNoAndroidSourcesToAnalyse() {
        TestProject project = projectRule.newProject()
        assumeThat(project).isAndroidProject()

        TestProject.Result result = project
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION)
                .withSourceSet('test', SOURCES_WITH_HIGH_VIOLATION)
                .withSourceSet('androidTest', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 1
                }''')
                .withToolsConfig('''spotbugs {
                    exclude project.android.sourceSets.test.java.srcDirs
                    exclude project.android.sourceSets.androidTest.java.srcDirs
                }''')
                .withAdditionalConfiguration(addCheckSpotbugsClassesTask())
                .build('checkSpotbugsClasses')

        Truth.assertThat(result.outcome(':checkSpotbugsClasses')).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    void shouldNotFailBuildWhenSpotBugsIsConfiguredMultipleTimes() {
        projectRule.newProject()
                .withSourceSet('main', SOURCES_WITH_LOW_VIOLATION)
                .withPenalty('none')
                .withToolsConfig("""
                    spotbugs { }
                    spotbugs {
                        ignoreFailures = false
                    }
                """)
                .build('check')
    }

    @Test
    void shouldBeUpToDateWhenCheckTaskRunsAgain() {
        def project = projectRule.newProject()
                .withSourceSet('debug', SOURCES_WITH_LOW_VIOLATION, SOURCES_WITH_MEDIUM_VIOLATION)
                .withSourceSet('release', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 10
                    maxWarnings = 10
                }''')
                .withToolsConfig('spotbugs {}')

        project.build('check')

        def result = project.build('check')

        Truth.assertThat(result.outcome(':spotbugsDebug')).isEqualTo(TaskOutcome.UP_TO_DATE)
        Truth.assertThat(result.outcome(':generateSpotbugsDebugHtmlReport')).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    @Test
    void shouldNotGenerateHtmlWhenDisabled() {
        def result = projectRule.newProject()
                .withSourceSet('main', SOURCES_WITH_LOW_VIOLATION)
                .withToolsConfig('''spotbugs { 
                    htmlReportEnabled false 
                }''')
                .build('check')

        Truth.assertThat(result.tasksPaths).doesNotContain(':generateSpotbugsDebugHtmlReport')
    }

    /**
     * The custom task created in the snippet below will check whether {@code SpotBugs} tasks with
     * empty {@code source} will have empty {@code classes} too. </p>
     */
    private String addCheckSpotbugsClassesTask() {
        '''
        project.task('checkSpotbugsClasses') {
            dependsOn project.tasks.findByName('evaluateViolations')
            doLast {
                project.tasks.withType(com.github.spotbugs.SpotBugsTask).all { spotbugs ->
                    if (spotbugs.source.isEmpty() && !spotbugs.classes.isEmpty()) {
                        throw new GradleException("${spotbugs.path}.source is empty but ${spotbugs.path}.classes is not: \\n${spotbugs.classes.files}")
                    }
                }
            }
        }'''
    }

}
