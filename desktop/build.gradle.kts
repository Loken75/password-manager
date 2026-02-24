plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation("com.github.mwiede:jsch:2.27.8")
    implementation("com.formdev:flatlaf:3.7")

    testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.passwordmanager.Main")
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("password-manager")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.passwordmanager.Main"
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}
