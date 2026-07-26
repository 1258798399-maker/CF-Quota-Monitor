package com.nova.cfquota.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GraphQlRequest(
    val query: String,
    val variables: Variables,
    /**
     * Per-request unique marker appended to the body. Cloudflare's GraphQL
     * endpoint tolerates (and ignores) unknown top-level fields, so this
     * guarantees every manual refresh produces a byte-distinct request body
     * that no intermediary proxy or local HTTP cache can ever serve from a
     * previous response.
     */
    val cacheBust: String
)

@Serializable
data class Variables(
    val accountTag: String,
    val dtGeq: String,
    val dtLeq: String
)

@Serializable
data class GraphQlResponse(
    val data: DataNode? = null,
    val errors: List<GraphQlError>? = null
)

@Serializable
data class GraphQlError(
    val message: String? = null
)

@Serializable
data class DataNode(
    val viewer: Viewer? = null
)

@Serializable
data class Viewer(
    val accounts: List<Account> = emptyList()
)

@Serializable
data class Account(
    @SerialName("workersInvocationsAdaptive")
    val workersGroups: List<RequestGroup> = emptyList(),
    @SerialName("pagesFunctionsInvocationsAdaptiveGroups")
    val pagesGroups: List<RequestGroup> = emptyList()
)

@Serializable
data class RequestGroup(
    val sum: RequestSum? = null
)

@Serializable
data class RequestSum(
    val requests: Long = 0L
)
