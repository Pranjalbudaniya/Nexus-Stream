plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("app/src/**/*.kt")
        targetExclude("**/build/**", ".gradle/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        targetExclude("**/build/**", ".gradle/**")
        ktlint(libs.versions.ktlint.get())
    }
}

tasks.matching { it.name.startsWith("spotless") || it.name == "hexGuard" }.configureEach {
    mustRunAfter(tasks.matching { it.name.endsWith("clean") || it.name.endsWith("Clean") })
}

abstract class HexGuardTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun scan() {
        val ktColorRegex = Regex("""Color\(\s*0[xX][0-9A-Fa-f]+""")
        val ktHexRegex = Regex(""""#[0-9A-Fa-f]{3,8}"""")
        val ktParseColorRegex = Regex("""parseColor\(""")
        val ktRgbRegex = Regex("""Color\.(rgb|argb)\(""")
        val xmlHexRegex = Regex("""#[0-9A-Fa-f]{6,8}""")

        val violations = mutableListOf<String>()

        for (file in sourceFiles) {
            val isKt = file.name.endsWith(".kt")
            val isXml = file.name.endsWith(".xml")
            if (!isKt && !isXml) continue

            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val lineNum = index + 1
                    if (isKt) {
                        val match =
                            ktColorRegex.find(line)
                                ?: ktHexRegex.find(line)
                                ?: ktParseColorRegex.find(line)
                                ?: ktRgbRegex.find(line)
                        if (match != null) {
                            violations.add("${file.path}:$lineNum: ${match.value}")
                        }
                    } else if (isXml) {
                        val match = xmlHexRegex.find(line)
                        if (match != null) {
                            violations.add("${file.path}:$lineNum: ${match.value}")
                        }
                    }
                }
            }
        }

        reportFile.get().asFile.writeText(
            if (violations.isEmpty()) "OK" else violations.joinToString("\n"),
        )

        if (violations.isNotEmpty()) {
            val message = violations.joinToString("\n")
            println(message)
            throw GradleException("HexGuard found hardcoded hex colors:\n$message")
        }
    }
}

val hexGuardTask =
    tasks.register<HexGuardTask>("hexGuard") {
        sourceFiles.setFrom(
            fileTree("app/src") {
                include("**/*.kt")
                exclude("**/presentation/theme/tokens/**")
            },
            fileTree("app/src") {
                include("**/res/values*/*.xml", "**/res/color/*.xml")
                exclude("**/ic_launcher*")
            },
        )
        reportFile.set(layout.buildDirectory.file("reports/hexGuard/report.txt"))
    }

tasks.register("checkAll") {
    group = "verification"
    description = "Runs assembleDebug, unit tests, detekt, spotlessCheck, and hexGuard"
    dependsOn(
        ":app:assembleDebug",
        ":app:testDebugUnitTest",
        ":app:detektDebug",
        ":app:detektDebugUnitTest",
        "spotlessCheck",
        hexGuardTask,
    )
}
