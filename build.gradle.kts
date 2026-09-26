plugins {
    java
    id("xyz.jpenilla.run-velocity") version "3.1.0"
}

group = "pl.blixy"
version = "2.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:4.1.1")
    annotationProcessor("com.velocitypowered:velocity-api:4.1.1")

    testImplementation("com.velocitypowered:velocity-api:4.1.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.io.tmpdir", temporaryDir.absolutePath)
}

// @Plugin only accepts constants, so the version is stamped into a generated BuildConstants class.
val generateTemplates by tasks.registering(Copy::class) {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/templates"))
    expand(props)
}

sourceSets.main { java.srcDir(generateTemplates.map { it.outputs }) }

tasks.runVelocity {
    velocityVersion("4.1.1")
}
