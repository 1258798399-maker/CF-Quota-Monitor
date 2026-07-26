package com.nova.cfquota.core

/**
 * Generic wrapper for representing the outcome of an operation that can fail.
 */
sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val type: ErrorType, val message: String) : Resource<Nothing>
}

enum class ErrorType {
    NETWORK,        // no connectivity / timeout
    UNAUTHORIZED,   // token invalid / permission denied
    RATE_LIMITED,   // API over limit (HTTP 429)
    GRAPHQL,        // GraphQL returned errors array
    UNKNOWN
}
