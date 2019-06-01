package com.novoda.staticanalysis.internal.spotbugs

import com.github.spotbugs.SpotBugsExtension
import com.github.spotbugs.SpotBugsTask
import com.novoda.staticanalysis.Violations
import com.novoda.staticanalysis.internal.CodeQualityConfigurator
import com.novoda.staticanalysis.internal.findbugs.CollectFindbugsViolationsTask
import com.novoda.staticanalysis.internal.findbugs.GenerateFindBugsHtmlReport
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSet

import java.nio.file.Path

import static com.novoda.staticanalysis.internal.TasksCompat.configureNamed
import static com.novoda.staticanalysis.internal.TasksCompat.createTask

class SpotBugsConfigurator extends CodeQualityConfigurator<SpotBugsTask, SpotBugsExtension> {

    protected boolean htmlReportEnabled = true

    SpotBugsConfigurator(Project project, Violations violations, Task evaluateViolations) {
        super(project, violations, evaluateViolations)
    }

    @Override
    protected String getToolName() {
        'spotbugs'
    }

    @Override
    protected Object getToolPlugin() {
        QuietSpotBugsPlugin
    }

    @Override
    protected Class<SpotBugsExtension> getExtensionClass() {
        SpotBugsExtension
    }

    @Override
    protected Class<?> getTaskClass() {
        SpotBugsTask
    }

    @Override
    protected Action<SpotBugsExtension> getDefaultConfiguration() {
        return { extension ->
            extension.ext.htmlReportEnabled = { boolean enabled -> this.htmlReportEnabled = enabled }
        }
    }

    @Override
    protected void configureAndroidWithVariants(DomainObjectSet variants) {
        if (configured) return

        variants.all { configureVariant(it) }
        variantFilter.filteredTestVariants.all { configureVariant(it) }
        variantFilter.filteredUnitTestVariants.all { configureVariant(it) }
        configured = true
    }


    @Override
    protected void configureVariant(variant) {
        createToolTaskForAndroid(variant)
        def collectViolations = createCollectViolations(getToolTaskNameFor(variant), violations)
        evaluateViolations.dependsOn collectViolations
    }

    @Override
    protected void createToolTaskForAndroid(variant) {
        createTask(project, getToolTaskNameFor(variant), QuietSpotBugsPlugin.Task) { task ->
            List<File> androidSourceDirs = variant.sourceSets.collect { it.javaDirectories }.flatten()
            task.description = "Run SpotBugs analysis for ${variant.name} classes"
            task.source = androidSourceDirs
            task.classpath = variant.javaCompile.classpath
            task.extraArgs '-auxclasspath', androidJar
            task.conventionMapping.map("classes") {
                List<String> includes = createIncludePatterns(task.source, androidSourceDirs)
                getAndroidClasses(javaCompile(variant), includes)
            }
            sourceFilter.applyTo(task)
            task.dependsOn javaCompile(variant)
        }
    }

    private FileCollection getAndroidClasses(javaCompile, List<String> includes) {
        includes.isEmpty() ? project.files() : project.fileTree(javaCompile.destinationDir).include(includes) as ConfigurableFileTree
    }

    @Override
    protected void configureJavaProject() {
        super.configureJavaProject()
        project.afterEvaluate {
            project.sourceSets.each { SourceSet sourceSet ->
                String taskName = sourceSet.getTaskName(toolName, null)
                configureNamed(project, taskName) { task ->
                    task.conventionMapping.map("classes") {
                        List<File> sourceDirs = sourceSet.allJava.srcDirs.findAll { it.exists() }.toList()
                        List<String> includes = createIncludePatterns(task.source, sourceDirs)
                        getJavaClasses(sourceSet, includes)
                    }
                }
            }
        }
    }

    private static List<String> createIncludePatterns(FileCollection sourceFiles, List<File> sourceDirs) {
        List<Path> includedSourceFilesPaths = sourceFiles.matching { '**/*.java' }.files.collect { it.toPath() }
        List<Path> sourceDirsPaths = sourceDirs.collect { it.toPath() }
        createRelativePaths(includedSourceFilesPaths, sourceDirsPaths)
                .collect { Path relativePath -> (relativePath as String) - '.java' + '*' }
    }

    private static List<Path> createRelativePaths(List<Path> includedSourceFiles, List<Path> sourceDirs) {
        includedSourceFiles.collect { Path sourceFile ->
            sourceDirs
                    .findAll { Path sourceDir -> sourceFile.startsWith(sourceDir) }
                    .collect { Path sourceDir -> sourceDir.relativize(sourceFile) }
        }.flatten()
    }

    private FileCollection getJavaClasses(SourceSet sourceSet, List<String> includes) {
        includes.isEmpty() ? project.files() : createClassesTreeFrom(sourceSet, includes)
    }

    private FileCollection createClassesTreeFrom(SourceSet sourceSet, List<String> includes) {
        return sourceSet.output.classesDirs.inject(null) { ConfigurableFileTree cumulativeTree, File classesDir ->
            def tree = project.fileTree(classesDir)
                    .builtBy(sourceSet.output)
                    .include(includes) as ConfigurableFileTree
            cumulativeTree?.plus(tree) ?: tree
        }
    }

    @Override
    protected void configureToolTask(SpotBugsTask task) {
        super.configureToolTask(task)
        task.reports.xml.enabled = true
        task.reports.html.enabled = false
    }

    @Override
    protected def createCollectViolations(String taskName, Violations violations) {
        if (htmlReportEnabled) {
            createHtmlReportTask(taskName)
        }
        createTask(project, "collect${taskName.capitalize()}Violations", CollectFindbugsViolationsTask) { task ->
            def spotbugs = project.tasks[taskName] as SpotBugsTask
            task.xmlReportFile = spotbugs.reports.xml.destination
            task.violations = violations

            if (htmlReportEnabled) {
                task.dependsOn project.tasks["generate${taskName.capitalize()}HtmlReport"]
            } else {
                task.dependsOn spotbugs
            }
        }
    }

    private void createHtmlReportTask(String taskName) {
        createTask(project, "generate${taskName.capitalize()}HtmlReport", GenerateFindBugsHtmlReport) { GenerateFindBugsHtmlReport task ->
            def spotbugs = project.tasks[taskName] as SpotBugsTask
            task.xmlReportFile = spotbugs.reports.xml.destination
            task.htmlReportFile = new File(task.xmlReportFile.absolutePath - '.xml' + '.html')
            task.classpath = spotbugs.spotbugsClasspath
            task.dependsOn spotbugs
        }
    }

    private def getAndroidJar() {
        "${project.android.sdkDirectory}/platforms/${project.android.compileSdkVersion}/android.jar"
    }
}
