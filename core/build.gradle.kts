plugins {
    `java-library`
}

dependencies {
    api("com.google.code.gson:gson:2.13.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
