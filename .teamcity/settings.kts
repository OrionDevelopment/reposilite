import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.projectFeatures.githubIssues
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.dockerCommand
/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2021.2"

project {

    buildType(Build)
    buildType(BuildSecondaryBranches)
    buildType(PullRequests)

    params {
        text("docker_jdk_version", "8", label = "Gradle version", description = "The version of the JDK to use during execution of tasks in a JDK.", display = ParameterDisplay.HIDDEN, allowEmpty = false)
        text("docker_gradle_version", "7.4.2", label = "Gradle version", description = "The version of Gradle to use during execution of Gradle tasks.", display = ParameterDisplay.HIDDEN, allowEmpty = false)
        text("git_main_branch", "master", label = "Git Main Branch", description = "The git main or default branch to use in VCS operations.", display = ParameterDisplay.HIDDEN, allowEmpty = false)
        text("github_repository_name", "reposilite", label = "The github repository name. Used to connect to it in VCS Roots.", description = "This is the repository slug on github. So for example `reposilite` or `MinecraftForge`. It is interpolated into the global VCS Roots.", display = ParameterDisplay.HIDDEN, allowEmpty = false)
        text("env.PUBLISHED_JAVA_ARTIFACT_ID", "reposilite", label = "Published artifact id", description = "The maven coordinate artifact id that has been published by this build. Can not be empty.", allowEmpty = false)
        text("env.PUBLISHED_JAVA_GROUP", "net.minecraftforge", label = "Published group", description = "The maven coordinate group that has been published by this build. Can not be empty.", allowEmpty = false)
        param("docker_additional_args", "-u 0")
    }

    features {
        githubIssues {
            id = "reposilite__IssueTracker"
            displayName = "MinecraftForge/reposilite"
            repositoryURL = "https://github.com/MinecraftForge/reposilite"
        }
    }
}

object Build : BuildType({
    templates(AbsoluteId("MinecraftForge_SetupGradleUtilsCiEnvironmen"), AbsoluteId("MinecraftForge_BuildWithDiscordNotifications"), AbsoluteId("MinecraftForge_BuildMainBranches"), AbsoluteId("MinecraftForge_BuildUsingGradle"), AbsoluteId("MinecraftForge_PublishProjectUsingGradle"), AbsoluteId("MinecraftForge_TriggersStaticFilesWebpageGenerator"))
    id("reposilite__Build")
    name = "Build"
    description = "Builds and Publishes the main branches of the project."

    steps {
        dockerCommand {
            name = "Build Image"
            id = "reposilite__build_image"
            commandType = build {
                source = file {
                    path = "Dockerfile"
                }
                contextDir = "."
                namesAndTags = """
                containers.minecraftforge.net/reposilite:latest
                containers.minecraftforge.net/reposilite:%env.BUILD_NUMBER%
            """.trimIndent()
                commandArgs = "--pull"
            }
            param("dockerImage.platform", "linux")
        }
        dockerCommand {
            name = "Push Image"
            id = "reposilite__push_image"
            commandType = push {
                namesAndTags = """
                containers.minecraftforge.net/reposilite:latest
                containers.minecraftforge.net/reposilite:%env.BUILD_NUMBER%
            """.trimIndent()
            }
        }
    }

    features {
        dockerSupport {
            id = "reposilite__DockerSupport"
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_7"
            }
        }
    }
})

object BuildSecondaryBranches : BuildType({
    templates(AbsoluteId("MinecraftForge_ExcludesBuildingDefaultBranch"), AbsoluteId("MinecraftForge_SetupGradleUtilsCiEnvironmen"), AbsoluteId("MinecraftForge_BuildWithDiscordNotifications"), AbsoluteId("MinecraftForge_BuildMainBranches"), AbsoluteId("MinecraftForge_BuildUsingGradle"))
    id("reposilite__BuildSecondaryBranches")
    name = "Build - Secondary Branches"
    description = "Builds and Publishes the secondary branches of the project."
})

object PullRequests : BuildType({
    templates(AbsoluteId("MinecraftForge_BuildPullRequests"), AbsoluteId("MinecraftForge_SetupGradleUtilsCiEnvironmen"), AbsoluteId("MinecraftForge_BuildWithDiscordNotifications"), AbsoluteId("MinecraftForge_BuildUsingGradle"))
    id("reposilite__PullRequests")
    name = "Pull Requests"
    description = "Builds pull requests for the project"
})
