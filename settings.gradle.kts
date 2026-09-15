// GitHub Actions 的 runner 位于国际网络，可达官方仓库但常连不上阿里云镜像；
// 中国大陆的开发沙箱则相反。用 GITHUB_ACTIONS 环境变量（仅 CI 会设置）做切换，
// 保证两种环境下插件与依赖都能稳定解析，避免"not found"。
// 注意：pluginManagement 块内无法引用外层脚本声明，因此直接用 System.getenv 内联判断。
pluginManagement {
    repositories {
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            google()
            mavenCentral()
            gradlePluginPortal()
        } else {
            // 本地/中国大陆开发环境：阿里云镜像优先加速，官方仓库兜底。
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            google()
            mavenCentral()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "ai-quota"
include(":app")