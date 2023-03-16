import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.10"
    id("com.github.johnrengelman.shadow") version "8.1.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.google.api-client:google-api-client:2.2.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-drive:v3-rev20230212-2.0.0")
    implementation("com.google.apis:google-api-services-driveactivity:v2-rev20230205-2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-Beta")
    implementation("net.sourceforge.tess4j:tess4j:5.6.0")
    implementation("org.slf4j:slf4j-simple:2.0.6")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
    }

    shadowJar {
        dependsOn(jar)
        archiveBaseName.set(rootProject.name)
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    create<Copy>("outputJar") {
        from(shadowJar)
        into("./out")
    }
}

application {
    mainClass.set("io.github.lambdynma.chickenminer.MainKt")
}