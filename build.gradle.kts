// Root project build file
// Shared configuration for all subprojects

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}

// Helper task to show all versions
tasks.register("showVersions") {
    doLast {
        subprojects.forEach { project ->
            println("${project.name}: ${project.version}")
        }
    }
}
