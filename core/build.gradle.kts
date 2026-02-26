plugins {
    `java-library`
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
