package com.divarsmartsearch.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs [block] on the IO dispatcher and converts any exception from the
 * local Room database (or a filter/network call within it, e.g. the
 * optional Anthropic API call) into a user-friendly AppResult.Error, so
 * ViewModels never need to catch exceptions directly.
 */
suspend fun <T> safeCall(block: suspend () -> T): AppResult<T> = withContext(Dispatchers.IO) {
    try {
        AppResult.Success(block())
    } catch (e: NoSuchElementException) {
        AppResult.Error(e.message ?: "مورد یافت نشد")
    } catch (e: Exception) {
        AppResult.Error(e.message ?: "خطایی در دیتابیس محلی رخ داد")
    }
}
