plugins {
    // Generates an aggregate CycloneDX SBOM (build/reports/bom.json) across all
    // modules, consumed by the CI dependency-vulnerability scan and attached to releases.
    id("org.cyclonedx.bom") version "1.10.0"
}

// Root coordinates are required by the cyclonedxBom task to identify the SBOM subject.
group = "com.passwordmanager"
version = (findProperty("appVersion") as String?) ?: "0.0.0"

tasks.named<org.cyclonedx.gradle.CycloneDxTask>("cyclonedxBom") {
    // Scan only the production runtime classpaths. This excludes Kotlin/Compose
    // *DependenciesMetadata and debug/test-only configs (e.g. ui-test-manifest),
    // whose versions are BOM-managed and not resolvable here, and keeps the SBOM
    // to what actually ships.
    setIncludeConfigs(listOf("^runtimeClasspath$", "^releaseRuntimeClasspath$"))
}

subprojects {
    if (name != "android") {
        apply(plugin = "java")

        configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        tasks.withType<JavaCompile> {
            options.encoding = "UTF-8"
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}
