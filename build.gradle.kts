plugins {
    java
    id("com.modrinth.minotaur") version "2.+"
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "me.skerik"
version = property("pluginVersion") as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // PlaceholderAPI (soft dependency, compile only)
    maven("https://repo.extendedclip.com/releases/") {
        content { includeGroup("me.clip") }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("me.clip:placeholderapi:${property("placeholderApiVersion")}")
    // Resolved at runtime by the PluginLoader (src/main/resources/libraries.txt), not shaded
    compileOnly("com.zaxxer:HikariCP:${property("hikariVersion")}")
    // The only shaded library (relocated, as bStats requires); everything else is resolved at runtime
    implementation("org.bstats:bstats-bukkit:${property("bstatsVersion")}")

    testImplementation("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    testImplementation("org.xerial:sqlite-jdbc:${property("sqliteVersion")}")
    testImplementation("com.zaxxer:HikariCP:${property("hikariVersion")}")
    testImplementation("org.slf4j:slf4j-simple:2.0.17")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 25
    // Warnings are errors: no deprecated API without an explicit, commented suppression
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED") // sqlite-jdbc loads a native library
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.processResources {
    val props = mapOf(
        "name" to project.property("pluginName"),
        "version" to project.version,
        "description" to project.property("pluginDescription"),
        "main" to project.property("pluginMain"),
        "loader" to project.property("pluginLoader"),
        "website" to project.property("pluginWebsite"),
        "apiVersion" to project.property("apiVersion"),
        "bstatsId" to project.property("bstatsId"),
        "sqliteVersion" to project.property("sqliteVersion"),
        "mariadbVersion" to project.property("mariadbVersion"),
        "hikariVersion" to project.property("hikariVersion"),
    )
    inputs.properties(props)
    filesMatching(listOf("paper-plugin.yml", "libraries.txt", "build.properties")) { expand(props) }
}

// The shaded jar is the release artifact; the plain jar task is switched off
tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier = ""
    relocate("org.bstats", "me.skerik.aifriend.libs.bstats")
    exclude("META-INF/maven/**", "META-INF/*.md")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.runServer {
    minecraftVersion(project.property("minecraftVersion") as String)
    // Override with -PrunDir=<path> to reuse an existing test server (its eula.txt, world, other plugins)
    (findProperty("runDir") as String?)?.let { runDirectory = file(it) }
    // In CI (smoke test) the EULA is accepted through an environment variable, locally through eula.txt
    if (System.getenv("CI") != null) {
        jvmArgs("-Dcom.mojang.eula.agree=true")
    }
}

modrinth {
    token = System.getenv("MODRINTH_TOKEN")
    projectId = project.property("modrinthId") as String
    versionNumber = project.version as String
    versionType = "release"
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll(project.property("minecraftVersion") as String)
    loaders.addAll("paper", "purpur", "folia")
    changelog = providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText
    syncBodyFrom = providers.fileContents(layout.projectDirectory.file("docs/modrinth.md")).asText
}
