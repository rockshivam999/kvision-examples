import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    val kotlinVersion: String by System.getProperties()
    id("kotlinx-serialization") version kotlinVersion
    kotlin("js") version kotlinVersion
}

version = "1.0.0-SNAPSHOT"
group = "com.example"

repositories {
    mavenCentral()
    jcenter()
    maven { url = uri("https://dl.bintray.com/kotlin/kotlin-eap") }
    maven { url = uri("https://kotlin.bintray.com/kotlinx") }
    maven { url = uri("https://dl.bintray.com/kotlin/kotlin-js-wrappers") }
    maven {
        url = uri("https://dl.bintray.com/gbaldeck/kotlin")
        metadataSources {
            mavenPom()
            artifact()
        }
    }
    maven { url = uri("https://dl.bintray.com/rjaros/kotlin") }
    mavenLocal()
}

// Versions
val kotlinVersion: String by System.getProperties()
val kvisionVersion: String by System.getProperties()

// Custom Properties
val webDir = file("src/main/web")
val electronDir = file("src/main/electron")

kotlin {
    target {
        browser {
            runTask {
                outputFileName = "main.bundle.js"
                devServer = KotlinWebpackConfig.DevServer(
                    open = false,
                    port = 3000,
                    proxy = mapOf(
                        "/kv/*" to "http://localhost:8080",
                        "/kvws/*" to mapOf("target" to "ws://localhost:8080", "ws" to true)
                    ),
                    contentBase = listOf("$buildDir/processedResources/Js/main")
                )
            }
            webpackTask {
                outputFileName = "main.bundle.js"
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }
    sourceSets["main"].dependencies {
        implementation(kotlin("stdlib-js"))
        implementation(npm("po2json"))
        implementation(npm("grunt"))
        implementation(npm("grunt-pot"))

        implementation(npm("electron-builder", "^22.7.0"))

        implementation("pl.treksoft:kvision:$kvisionVersion")
        implementation("pl.treksoft:kvision-bootstrap:$kvisionVersion")
        implementation("pl.treksoft:kvision-bootstrap-css:$kvisionVersion")
        implementation("pl.treksoft:kvision-bootstrap-datetime:$kvisionVersion")
        implementation("pl.treksoft:kvision-bootstrap-select:$kvisionVersion")
        implementation("pl.treksoft:kvision-bootstrap-spinner:$kvisionVersion")
        implementation("pl.treksoft:kvision-bootstrap-upload:$kvisionVersion")
        implementation("pl.treksoft:kvision-bootstrap-dialog:$kvisionVersion")
        implementation("pl.treksoft:kvision-fontawesome:$kvisionVersion")
        implementation("pl.treksoft:kvision-i18n:$kvisionVersion")
        implementation("pl.treksoft:kvision-richtext:$kvisionVersion")
        implementation("pl.treksoft:kvision-handlebars:$kvisionVersion")
        implementation("pl.treksoft:kvision-datacontainer:$kvisionVersion")
        implementation("pl.treksoft:kvision-redux:$kvisionVersion")
        implementation("pl.treksoft:kvision-chart:$kvisionVersion")
        implementation("pl.treksoft:kvision-tabulator:$kvisionVersion")
        implementation("pl.treksoft:kvision-pace:$kvisionVersion")
        implementation("pl.treksoft:kvision-moment:$kvisionVersion")
        implementation("pl.treksoft:kvision-electron:$kvisionVersion")
    }
    sourceSets["test"].dependencies {
        implementation(kotlin("test-js"))
        implementation("pl.treksoft:kvision-testutils:$kvisionVersion:tests")
    }
    sourceSets["main"].resources.srcDir(webDir)
}

fun getNodeJsBinaryExecutable(): String {
    val nodeDir = NodeJsRootPlugin.apply(project).nodeJsSetupTask.destination
    val isWindows = System.getProperty("os.name").toLowerCase().contains("windows")
    val nodeBinDir = if (isWindows) nodeDir else nodeDir.resolve("bin")
    val command = NodeJsRootPlugin.apply(project).nodeCommand
    val finalCommand = if (isWindows && command == "node") "node.exe" else command
    return nodeBinDir.resolve(finalCommand).absolutePath
}

tasks {
    create("generateGruntfile") {
        outputs.file("$buildDir/js/Gruntfile.js")
        doLast {
            file("$buildDir/js/Gruntfile.js").run {
                writeText(
                    """
                    module.exports = function (grunt) {
                        grunt.initConfig({
                            pot: {
                                options: {
                                    text_domain: "messages",
                                    dest: "../../src/main/resources/i18n/",
                                    keywords: ["tr", "ntr:1,2", "gettext", "ngettext:1,2"],
                                    encoding: "UTF-8"
                                },
                                files: {
                                    src: ["../../src/main/kotlin/**/*.kt"],
                                    expand: true,
                                },
                            }
                        });
                        grunt.loadNpmTasks("grunt-pot");
                    };
                """.trimIndent()
                )
            }
        }
    }
    create("generatePotFile", Exec::class) {
        dependsOn("compileKotlinJs", "generateGruntfile")
        workingDir = file("$buildDir/js")
        executable = getNodeJsBinaryExecutable()
        args("$buildDir/js/node_modules/grunt/bin/grunt", "pot")
        inputs.files(kotlin.sourceSets["main"].kotlin.files)
        outputs.file("$projectDir/src/main/resources/i18n/messages.pot")
    }
}
afterEvaluate {
    tasks {
        getByName("processResources", Copy::class) {
            dependsOn("compileKotlinJs")
            exclude("**/*.pot")
            doLast("Convert PO to JSON") {
                destinationDir.walkTopDown().filter {
                    it.isFile && it.extension == "po"
                }.forEach {
                    exec {
                        executable = getNodeJsBinaryExecutable()
                        args(
                            "$buildDir/js/node_modules/po2json/bin/po2json",
                            it.absolutePath,
                            "${it.parent}/${it.nameWithoutExtension}.json",
                            "-f",
                            "jed1.x"
                        )
                        println("Converted ${it.name} to ${it.nameWithoutExtension}.json")
                    }
                    it.delete()
                }
            }
        }
        create("zip", Zip::class) {
            dependsOn("browserProductionWebpack")
            group = "package"
            destinationDirectory.set(file("$buildDir/libs"))
            val distribution =
                project.tasks.getByName("browserProductionWebpack", KotlinWebpack::class).destinationDirectory!!
            from(distribution) {
                include("*.*")
            }
            from(webDir)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            inputs.files(distribution, webDir)
            outputs.file(archiveFile)
        }
        create("buildApp", Copy::class) {
            dependsOn("browserProductionWebpack")
            group = "build"
            val distribution =
                project.tasks.getByName("browserProductionWebpack", KotlinWebpack::class).destinationDirectory
            from(distribution, webDir, electronDir)
            inputs.files(distribution, webDir, electronDir)
            into("$buildDir/dist")
        }
        create("runApp", Exec::class) {
            dependsOn("buildApp")
            group = "run"
            workingDir = file("$buildDir/dist")
            executable = getNodeJsBinaryExecutable()
            args("$buildDir/js/node_modules/electron/cli.js", ".")
        }
        create("bundleApp", Exec::class) {
            dependsOn("buildApp")
            group = "package"
            doFirst {
                val targetDir = file("$buildDir/electron")
                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }
                targetDir.mkdirs()
            }
            workingDir = file("$buildDir/dist")
            executable = getNodeJsBinaryExecutable()
            args("$buildDir/js/node_modules/electron-builder/out/cli/cli.js", "--config")
        }
    }
}
