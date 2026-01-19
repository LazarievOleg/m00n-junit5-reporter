plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
}

group = "io.m00nreport"
version = "1.2.9"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // JUnit 5 Platform (provided - users bring their own)
    compileOnly(platform("org.junit:junit-bom:5.11.3"))
    compileOnly("org.junit.jupiter:junit-jupiter-api")
    compileOnly("org.junit.platform:junit-platform-launcher")
    
    // AspectJ Runtime - included transitively for @Step support
    api("org.aspectj:aspectjrt:1.9.22")
    
    // AspectJ Weaver - users must add this + javaagent for automatic step tracking
    compileOnly("org.aspectj:aspectjweaver:1.9.22")
    
    // HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON processing
    implementation("com.google.code.gson:gson:2.11.0")
    
    // Logging (SLF4J API - users bring their own impl)
    implementation("org.slf4j:slf4j-api:2.0.16")
    
    // Test dependencies
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("ch.qos.logback:logback-classic:1.5.12")
}

tasks.test {
    useJUnitPlatform()
}

// Javadoc configuration
tasks.javadoc {
    options {
        (this as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addStringOption("Xdoclint:none", "-quiet")
        }
    }
}

// ============================================================================
// Publishing Configuration
// ============================================================================

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            
            pom {
                name.set("M00n JUnit5 Reporter")
                description.set("JUnit 5 reporter for M00n Report - real-time test result streaming with retry tracking, attachments, and step reporting")
                url.set("https://github.com/m00nsolutions/m00nreport")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("m00nreport")
                        name.set("M00N Team")
                        email.set("support@m00nreport.io")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/m00nsolutions/m00nreport.git")
                    developerConnection.set("scm:git:ssh://github.com:m00nsolutions/m00nreport.git")
                    url.set("https://github.com/m00nsolutions/m00nreport")
                }
            }
        }
    }
    
    repositories {
        // Maven Central via Sonatype OSSRH
        maven {
            name = "OSSRH"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            
            credentials {
                username = findProperty("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME")
                password = findProperty("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD")
            }
        }
        
        // GitHub Packages
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/m00nsolutions/m00nreport")
            credentials {
                username = findProperty("gpr.user")?.toString() ?: System.getenv("GITHUB_ACTOR")
                password = findProperty("gpr.key")?.toString() ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// Signing for Maven Central
signing {
    // Only sign if GPG key is available
    isRequired = gradle.taskGraph.hasTask("publishToSonatype")
    
    val signingKeyId = findProperty("signing.keyId")?.toString() ?: System.getenv("SIGNING_KEY_ID")
    val signingKey = findProperty("signing.key")?.toString() ?: System.getenv("SIGNING_KEY")
    val signingPassword = findProperty("signing.password")?.toString() ?: System.getenv("SIGNING_PASSWORD")
    
    if (signingKeyId != null && signingKey != null) {
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    }
    
    sign(publishing.publications["mavenJava"])
}

// Skip signing if not publishing to Maven Central
tasks.withType<Sign>().configureEach {
    onlyIf { gradle.taskGraph.hasTask("publishMavenJavaPublicationToOSSRHRepository") }
}
