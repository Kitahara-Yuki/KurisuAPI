package com.kurisuapi.util

fun String.containsAny(vararg keywords: String): Boolean =
    keywords.any { this.contains(it) }
