
plugins {
    id("org.gradle.java-library")
    id("org.sonarqube") version "7.2.1.6560"
}

buildscript {
    repositories {
        mavenCentral()
    }
}

sonar {
    properties {
        property("sonar.projectKey", "Darkbot-Plugins_SharedPlugin")
        property("sonar.organization", "darkbot-plugins")
    }
}

repositories {
    mavenLocal()
    mavenCentral()

    maven { url = uri("https://jitpack.io") }
}

allprojects {
    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

group = "dev.shared"
version = "0.0.0"
description = "SharedPlugin"

dependencies {
    api("eu.darkbot.DarkBotAPI", "darkbot-impl", "0.9.5")
    api("eu.darkbot", "DarkBot", "97430f3417")
}

tasks.withType<Jar> {
    archiveFileName.set("SharedPlugin.jar")
}

tasks.register<Exec>("signFile") {
    dependsOn("jar")
    commandLine("cmd", "/c", "sign.bat")
}

tasks.register("copyFile") {
    dependsOn("signFile")
    doLast {
        copy {
            from(layout.buildDirectory.file("libs/SharedPlugin.jar"))
            into(layout.projectDirectory)
        }
    }
}