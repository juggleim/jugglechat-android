import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency

val boundaryConfigurations = setOf(
    "api",
    "implementation",
    "compileOnly",
    "runtimeOnly",
    "testImplementation",
    "androidTestImplementation"
)

fun isFeatureModule(name: String): Boolean = name.startsWith("feature-")
fun isCoreModule(name: String): Boolean = name.startsWith("core-")

val verifyModuleBoundaries by rootProject.tasks.registering {
    group = "verification"
    description = "校验模块依赖方向，防止 feature 互相依赖与 core 反向依赖 feature。"

    doLast {
        val violations = mutableListOf<String>()

        rootProject.subprojects.forEach { sourceProject ->
            val projectDependencies = sourceProject.configurations
                .filter { it.name in boundaryConfigurations }
                .flatMap { configuration ->
                    configuration.dependencies
                        .withType(ProjectDependency::class.java)
                        .map { it.dependencyProject }
                }
                .distinctBy { it.path }

            projectDependencies.forEach { targetProject ->
                val fromName = sourceProject.name
                val toName = targetProject.name

                if (isFeatureModule(fromName)
                    && isFeatureModule(toName)
                    && fromName != toName
                    && !toName.endsWith("-api")
                ) {
                    violations += "禁止 feature 直接依赖其他 feature 实现: :$fromName -> :$toName"
                }

                if (isCoreModule(fromName) && isFeatureModule(toName)) {
                    violations += "禁止 core 反向依赖 feature: :$fromName -> :$toName"
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("模块边界校验失败：")
                    violations.forEach { appendLine(" - $it") }
                }
            )
        }
    }
}

rootProject.allprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(verifyModuleBoundaries)
    }
}
