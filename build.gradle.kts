import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.researchgate.release.GitAdapter.GitConfig
import org.jetbrains.dokka.utilities.cast

plugins {
    application
    id("java-library")
    id("maven-publish")
    id("net.researchgate.release") version "2.6.0"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("org.jetbrains.dokka") version "1.6.10"
//    id("com.osacky.doctor") version "0.7.0"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://clyze.jfrog.io/artifactory/default-maven-local") }
    maven {
        url = uri("http://centauri.di.uoa.gr:8081/artifactory/plast-public")
        isAllowInsecureProtocol = true
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

allprojects {
    group = "org.clyze"
}

application {
    mainClass.set("org.clyze.source.irfitter.Main")
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
    gradleVersion = "7.3.3"
}

val asmVersion = "9.2"
dependencies {
    implementation("commons-cli:commons-cli:1.5.0")
    implementation("org.clyze:clue-common:3.25.3")
    implementation("org.clyze:metadata-model:2.4.1")
    implementation("org.clyze:code-processor:4.24.10")
    // SARIF library
    implementation("org.clyze:mini-sarif:0.1.3")
    // Bytecode parser, BSD
    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")
    // Java parser, Apache 2.0
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.23.1")
    // Groovy parser, Apache 2.0
    api("org.codehaus.groovy:groovy:3.0.9")
    // Kotlin parser, Apache 2.0
    api("org.antlr.grammars:kotlin-formal:1.0-SNAPSHOT")
    // Dex parser, BSD
    api("org.smali:dexlib2:2.5.2")
    // Zip library, Apache 2.0
    implementation("org.zeroturnaround:zt-zip:1.14")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("commons-io:commons-io:2.11.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

tasks.test {
    useJUnitPlatform()
}

/**
 * Checks for dependencies that may come from mavenLocal and can harm
 * build reproducibility (e.g. for releases).
 */
project.afterEvaluate {
    configurations.default.get()
        .map { it.canonicalPath }
        .filter { it.contains(".m2") }
        .forEach { println("WARNING: build may be using mavenLocal(): $it") }
}

tasks {
    named<Javadoc>("javadoc") {
        val options = options as StandardJavadocDocletOptions
        options.addBooleanOption("Xdoclint:all", true)
    }

    withType<Jar> {
        manifest {
            attributes("Implementation-Version" to archiveVersion.get())
        }
    }
}

val shadowJar by tasks.getting(ShadowJar::class) {
    // Set classifier to "null", to make the shadow JAR the main published artifact.
    archiveClassifier.set(null as String?)
}
val sourcesJar = tasks.getByName("sourcesJar")
val javadocJar = tasks.getByName("javadocJar")

artifacts {
    add("archives", shadowJar)
    add("archives", sourcesJar)
    add("archives", javadocJar)
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            artifact(shadowJar)
        }
    }
}

if (project.hasProperty("artifactory_user")) {
    publishing {
        repositories {
            maven {
                credentials {
                    username = project.properties["artifactory_user"] as String
                    password = project.properties["artifactory_password"] as String
                }
                //Always publish to the public releases repo
                val artifactoryContextUrl = project.properties["artifactory_contextUrl"] as String
                url = uri("$artifactoryContextUrl/libs-public-release-local")
                isAllowInsecureProtocol = true
            }
        }
    }
}

release {
    failOnSnapshotDependencies = false
    getProperty("git").cast<GitConfig>().apply {
        requireBranch = "main"
    }
}
tasks.getByName("afterReleaseBuild").dependsOn(tasks.getByName("publish"))
