plugins {
    id("java")
    application
}

group = "org.example"
version = "1.0"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-tree:9.8")
    implementation("org.ow2.asm:asm-analysis:9.8")
    implementation("org.ow2.asm:asm-util:9.8")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("org.example.metrics.Main")
}

val agentJar = tasks.register<Jar>("agentJar") {
    archiveFileName.set("coverage-agent.jar")
    from(sourceSets.main.get().output) { include("org/example/agent/**") }
    manifest { attributes("Premain-Class" to "org.example.agent.CoverageAgent") }
}

tasks.assemble { dependsOn(agentJar) }

tasks.test {
    useJUnitPlatform()
    dependsOn(agentJar)
    systemProperty("agent.jar", agentJar.get().archiveFile.get().asFile.absolutePath)
}
