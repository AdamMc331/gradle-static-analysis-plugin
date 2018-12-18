package com.novoda.staticanalysis.internal.spotbugs

import com.google.common.truth.Truth
import com.novoda.test.TestAndroidProject
import com.novoda.test.TestProject
import com.novoda.test.TestProjectRule
import org.junit.Rule
import org.junit.Test

import static com.novoda.test.Fixtures.Findbugs.SOURCES_WITH_HIGH_VIOLATION
import static com.novoda.test.Fixtures.Findbugs.SOURCES_WITH_LOW_VIOLATION
import static com.novoda.test.LogsSubject.assertThat

class SpotBugsAndroidVariantIntegrationTest {

    public static final String SPOTBUGS_PLUGIN_VERSION = '1.6.6'

    @Rule
    public final TestProjectRule<TestAndroidProject> projectRule = TestProjectRule.forAndroidProject()

    @Test
    public void shouldNotFailBuildWhenSpotBugsViolationsBelowThresholdInDefaultApplicationVariant() {
        TestProject.Result result = createProject()
                .withSourceSet('main', SOURCES_WITH_LOW_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 2
                }''')
                .build('check')

        assertThat(result.logs).doesNotContainLimitExceeded()
        assertThat(result.logs).containsSpotBugsViolations(0, 2,
                result.buildFileUrl('reports/spotbugs/debug.html'),
                result.buildFileUrl('reports/spotbugs/release.html'))
    }

    @Test
    public void shouldFailBuildWhenSpotBugsViolationsOverThresholdInUnitTestVariant() {
        TestProject.Result result = createProject()
                .withSourceSet('main', SOURCES_WITH_LOW_VIOLATION)
                .withSourceSet('test', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 2
                }''')
                .buildAndFail('check')

        assertThat(result.logs).containsLimitExceeded(2, 0)
        assertThat(result.logs).containsSpotBugsViolations(2, 2,
                result.buildFileUrl('reports/spotbugs/debug.html'),
                result.buildFileUrl('reports/spotbugs/release.html'),
                result.buildFileUrl('reports/spotbugs/debugUnitTest.html'),
                result.buildFileUrl('reports/spotbugs/releaseUnitTest.html'))
    }


    @Test
    public void shouldFailBuildWhenSpotBugsViolationsOverThresholdInAndroidTestVariant() {
        TestProject.Result result = createProject()
                .withSourceSet('main', SOURCES_WITH_LOW_VIOLATION)
                .withSourceSet('androidTest', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 2
                }''')
                .buildAndFail('check')

        assertThat(result.logs).containsLimitExceeded(1, 0)
        assertThat(result.logs).containsSpotBugsViolations(1, 2,
                result.buildFileUrl('reports/spotbugs/debug.html'),
                result.buildFileUrl('reports/spotbugs/release.html'),
                result.buildFileUrl('reports/spotbugs/debugAndroidTest.html'))
    }

    @Test
    public void shouldFailBuildWhenSpotBugsViolationsOverThresholdInActiveProductFlavorVariant() {
        TestProject.Result result = createProject()
                .withSourceSet('main', SOURCES_WITH_LOW_VIOLATION)
                .withSourceSet('demo', SOURCES_WITH_HIGH_VIOLATION)
                .withSourceSet('full', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 0
                    maxWarnings = 2
                }''')
                .withAdditionalAndroidConfig('''
                    flavorDimensions 'tier'
                    productFlavors {
                        demo { dimension 'tier' }
                        full { dimension 'tier' }
                    }

                    variantFilter { variant ->
                        if(variant.name.contains('full')) {
                            variant.setIgnore(true);
                        }
                    }
                ''')
                .buildAndFail('check')

        assertThat(result.logs).containsLimitExceeded(2, 0)
        assertThat(result.logs).containsSpotBugsViolations(2, 2,
                result.buildFileUrl('reports/spotbugs/demoDebug.html'),
                result.buildFileUrl('reports/spotbugs/demoRelease.html'))
    }

    @Test
    public void shouldContainSpotBugsTasksForAllVariantsByDefault() {
        TestProject.Result result = createProject()
                .withSourceSet('main', SOURCES_WITH_LOW_VIOLATION)
                .withSourceSet('demo', SOURCES_WITH_HIGH_VIOLATION)
                .withSourceSet('full', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 1
                    maxWarnings = 1
                }''')
                .withAdditionalAndroidConfig('''
                    flavorDimensions 'tier'
                    productFlavors {
                        demo { dimension 'tier' }
                        full { dimension 'tier' }
                    }
                ''')
                .buildAndFail('check')

        assertThat(result.logs).containsLimitExceeded(3, 3)
        assertThat(result.logs).containsSpotBugsViolations(4, 4,
                result.buildFileUrl('reports/spotbugs/demoDebug.html'),
                result.buildFileUrl('reports/spotbugs/demoRelease.html'),
                result.buildFileUrl('reports/spotbugs/fullDebug.html'),
                result.buildFileUrl('reports/spotbugs/fullRelease.html'))
        Truth.assertThat(result.tasksPaths.findAll { it.startsWith(':spotbugs') }).containsAllIn([
                ':spotbugsDemoDebug',
                ':spotbugsDemoDebugUnitTest',
                ':spotbugsDemoDebugAndroidTest',
                ':spotbugsDemoRelease',
                ':spotbugsDemoReleaseUnitTest',
                ':spotbugsFullDebug',
                ':spotbugsFullDebugUnitTest',
                ':spotbugsFullRelease',
                ':spotbugsFullReleaseUnitTest'])
    }

    @Test
    public void shouldContainSpotBugsTasksForIncludedVariantsOnly() {
        TestProject.Result result = createProject()
                .withSourceSet('main', SOURCES_WITH_LOW_VIOLATION)
                .withSourceSet('demo', SOURCES_WITH_HIGH_VIOLATION)
                .withSourceSet('full', SOURCES_WITH_HIGH_VIOLATION)
                .withPenalty('''{
                    maxErrors = 1
                    maxWarnings = 1
                }''')
                .withAdditionalAndroidConfig('''
                    flavorDimensions 'tier'
                    productFlavors {
                        demo { dimension 'tier' }
                        full { dimension 'tier' }
                    }
                ''')
                .withToolsConfig('''
                    spotbugs {
                        includeVariants { variant -> variant.name.equals('demoDebug') }
                    }
                ''')
                .build('check')

        assertThat(result.logs).doesNotContainLimitExceeded()
        assertThat(result.logs).containsSpotBugsViolations(1, 1,
                result.buildFileUrl('reports/spotbugs/demoDebug.html'))
        def spotbugsTasks = result.tasksPaths.findAll { it.startsWith(':spotbugs') }
        Truth.assertThat(spotbugsTasks).containsAllIn([':spotbugsDemoDebug'])
        Truth.assertThat(spotbugsTasks).containsNoneIn([
                ':spotbugsDemoDebugUnitTest',
                ':spotbugsDemoDebugAndroidTest',
                ':spotbugsDemoRelease',
                ':spotbugsDemoReleaseUnitTest',
                ':spotbugsFullDebug',
                ':spotbugsFullDebugUnitTest',
                ':spotbugsFullRelease',
                ':spotbugsFullReleaseUnitTest'])
    }

    private TestAndroidProject createProject() {
        projectRule.newProject()
//                .withAdditionalBuildscriptConfig('''
//                    dependencies { classpath "gradle.plugin.com.github.spotbugs:spotbugs-gradle-plugin:1.6.6" }
//                ''')
                .withToolsConfig('spotbugs {}')
    }

}
