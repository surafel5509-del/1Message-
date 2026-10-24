package com.onemessages.app.di

import javax.inject.Qualifier

/** Process-lifetime coroutine scope for work that must outlive a screen (receivers, etc.). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
