package top.wkbin.tianxuan.core.tools

import top.wkbin.tianxuan.core.model.RuntimeName
import top.wkbin.tianxuan.core.model.RuntimeRequirement
import top.wkbin.tianxuan.core.model.ToolDependency
import top.wkbin.tianxuan.core.model.ToolManifest

class DependencyResolver() {
    fun resolve(manifest: ToolManifest): List<RuntimeRequirement> = manifest.dependencies.mapNotNull { dependency ->
        ManifestDependencyParser.parse(dependency)?.let { parsed ->
            when (parsed.name) {
                "node" -> RuntimeRequirement(RuntimeName.NODE, parsed.constraint)
                "python" -> RuntimeRequirement(RuntimeName.PYTHON, parsed.constraint)
                "git" -> RuntimeRequirement(RuntimeName.GIT, parsed.constraint)
                "ca-certificates" -> RuntimeRequirement(RuntimeName.CA_CERTIFICATES, parsed.constraint)
                "curl" -> RuntimeRequirement(RuntimeName.CURL, parsed.constraint)
                else -> null
            }
        }
        }

    fun resolveDependency(dependency: ToolDependency): RuntimeRequirement? = when (dependency) {
        is ToolDependency.Runtime -> RuntimeRequirement(dependency.name, dependency.constraint)
        else -> null
    }
}
