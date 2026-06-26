package com.aris.voice.domain

enum class RetryPolicy {
    NONE,
    ONCE,
    THREE_TIMES,
    EXPONENTIAL_BACKOFF
}
