plugins {
    `java-library`
    jacoco
}

val appVersion: String by project

tasks.register("generateVersionProperties") {
    val outputDir = layout.buildDirectory.dir("generated/resources/version")
    outputs.dir(outputDir)
    inputs.property("version", appVersion)
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("version.properties").writeText("app.version=$appVersion\n")
    }
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/resources/version"))
}

tasks.named("processResources") {
    dependsOn("generateVersionProperties")
}

dependencies {
    api("com.google.code.gson:gson:2.13.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Coverage gate: fails the build if :core line coverage regresses below the floor.
// Calibrated below current coverage so it guards against regressions without
// blocking on the existing baseline.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("jacocoTestReport"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}
