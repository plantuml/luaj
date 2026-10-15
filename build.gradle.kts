plugins {
    `java-library`
}

group = "net.plantuml"
version = "0.1.0-SNAPSHOT"

sourceSets {
    main {
        java { setSrcDirs(listOf("src/main/java")) }
    }
    test {
        java { setSrcDirs(listOf("src/test/java")) }
        resources { setSrcDirs(listOf("src/test/resources")) }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Java 11 is the baseline shared with the TeaVM (JS and C) builds.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(11)
}
