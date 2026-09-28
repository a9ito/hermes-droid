package com.a9ito.hermesagent.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * GET /v1/skills — deterministic listing (name, description, category), the same
 * set ``/skills list`` shows. Confirmed against api_server.py::_handle_skills,
 * which returns {"object":"list","data":[{name, description, category, ...}]}.
 */
@Serializable
data class SkillDto(
    val name: String,
    val description: String? = null,
    val category: String? = null,
)

@Serializable
data class SkillListResponse(
    val data: List<SkillDto> = emptyList(),
)

/**
 * GET /v1/toolsets — each configurable toolset with enabled/configured state and
 * the concrete tool names it expands to. Confirmed against
 * api_server.py::_handle_toolsets.
 */
@Serializable
data class ToolsetDto(
    val name: String,
    val label: String? = null,
    val description: String? = null,
    val enabled: Boolean = false,
    val configured: Boolean = false,
    val tools: List<String> = emptyList(),
)

@Serializable
data class ToolsetListResponse(
    val data: List<ToolsetDto> = emptyList(),
)
