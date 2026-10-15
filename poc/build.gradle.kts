// Proof of concept: compile luaj (the sources of the parent directory) with TeaVM
// to JavaScript and to C, and run the same test program on the three editions
// (JVM, JavaScript, C) to compare their outputs.
//
// This build is independent of the one in the parent directory. Use the wrapper
// of the parent:  ../gradlew <task>   (see README.md in this directory).

plugins {
    java
    application
    id("org.teavm") version "0.11.0" // change here to try another TeaVM version
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(11)
}

sourceSets {
    main {
        java {
            // the library itself, shared with the parent build
            srcDir("../src/main/java")
            // command-line tools of the library: default package, files, reflection
            exclude("lua.java", "luac.java")
        }
    }
}

val pocMain = "org.luaj.poc.Main"

application {
    mainClass.set(pocMain)
}

teavm {
    all {
        mainClass.set(pocMain)
    }
    js {
        targetFileName.set("luaj-poc.js")
    }
    c {
        // default options; the generated files are in build/generated/teavm/c
    }
}

// Reference output: the test program on the JVM, without the timing lines
// ('#' lines are timings: they are not comparable).
tasks.register<JavaExec>("jvmReference") {
    group = "verification"
    description = "Runs the test program on the JVM and writes build/jvm.txt"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(pocMain)
    val raw = layout.buildDirectory.file("jvm-raw.txt")
    val filtered = layout.buildDirectory.file("jvm.txt")
    doFirst {
        raw.get().asFile.parentFile.mkdirs()
        standardOutput = raw.get().asFile.outputStream()
    }
    doLast {
        filtered.get().asFile.writeText(
            raw.get().asFile.readLines(Charsets.UTF_8)
                .filter { !it.startsWith("#") }
                .joinToString("\n") + "\n"
        )
    }
}
