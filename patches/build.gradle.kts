import org.gradle.api.attributes.java.TargetJvmVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.zip.ZipFile

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

group = "app.epxec.patches"

patches {
    // TODO: Update this section with your project details.
    about {
        name = "Epxec Patches"
        description = "Patches for apps I like"
        source = "git@github.com:alecblance/android-patches.git"
        author = "Epxec"
        contact = "na"
        website = "na"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath: Configuration by configurations.creating
val universalLoader: Configuration by configurations.creating

configurations.named("compileClasspath") {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }
}

dependencies {
    compileOnly("app.epxec:morphe-universal-loader:0.1.4")
    universalLoader("app.epxec:morphe-universal-loader:0.1.4") {
        isTransitive = false
    }
    implementation(libs.morphe.patches.library)
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release.set(17)
    }

    withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        val loaderFiles = files(universalLoader)
        inputs.files(loaderFiles)

        doLast {
            val outDir = destinationDirectory.get().asFile
            loaderFiles.files.forEach { jarFile ->
                ZipFile(jarFile).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".class") }
                        .forEach { entry ->
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            if (bytes.size >= 8 && bytes[6] == 0.toByte() && bytes[7] == 65.toByte()) {
                                bytes[7] = 61.toByte()
                            }
                            val outFile = File(outDir, entry.name)
                            outFile.parentFile.mkdirs()
                            outFile.writeBytes(bytes)
                        }
                }
            }
        }
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
