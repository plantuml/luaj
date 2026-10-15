// Proof of concept: compile luaj (the sources of the parent directory) with TeaVM
// to JavaScript and to C, and run the same test program on the three editions
// (JVM, JavaScript, C) to compare their outputs.
//
// This build is independent of the one in the parent directory. Use the wrapper
// of the parent:  ../gradlew <task>   (see README.md in this directory).

plugins {
    java
    application
    id("org.teavm") version "0.16.0" // same version as the PlantUML build
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
    js {
        mainClass.set(pocMain)
        targetFileName.set("luaj-poc.js")
        moduleType.set(org.teavm.gradle.api.JSModuleType.COMMON_JS)
        obfuscated.set(false)
    }
    c {
        mainClass.set(pocMain)
        obfuscated.set(false)
        shortFileNames.set(false)
        // same setting as the PlantUML build (AGGRESSIVE is the default of TeaVM C)
        optimization.set(org.teavm.gradle.api.OptimizationLevel.BALANCED)
        // Heap size in MB. TeaVM C starts with a heap of 1 MB (default 1 to 16): the program
        // reports "free 0 MB of 1 MB" and garbage-collects all the time, and the first runs
        // crashed (bus error / segmentation fault) while the heap was growing. A fixed heap
        // (minimum = maximum) avoids both.
        minHeapSize.set(256)
        maxHeapSize.set(256)
        // outputDir defaults to build/generated/teavm/c
    }
}

// TeaVM only writes the C sources: add a CMakeLists.txt next to them so the
// native build is just "cmake + build" (the executable is bin/poc-c).
val copyCMakeLists = tasks.register<Copy>("copyCMakeLists") {
    from("CMakeLists.txt")
    into(layout.buildDirectory.dir("generated/teavm/c"))
}
tasks.matching { it.name == "generateC" }.configureEach {
    finalizedBy(copyCMakeLists)
}
copyCMakeLists.configure {
    mustRunAfter("generateC")
}

// teavm-classlib 0.15+ is published for JVM 17+ only and the TeaVM plugin adds it to
// compileClasspath: with --release 11 Gradle refuses to resolve it. The sources never
// need it (only the TeaVM compiler does, with its own classpath).
configurations.compileClasspath {
    exclude(group = "org.teavm", module = "teavm-classlib")
}

// TeaVM 0.15+ must RUN on Java 17+ (the bytecode stays Java 11).
tasks.matching { it.name == "generateJavaScript" || it.name == "generateC" }.configureEach {
    doFirst {
        check(JavaVersion.current() >= JavaVersion.VERSION_17) {
            "TeaVM requires running Gradle on JDK 17+ (current: ${JavaVersion.current()})."
        }
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
