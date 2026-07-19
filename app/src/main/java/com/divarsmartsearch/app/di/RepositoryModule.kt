package com.divarsmartsearch.app.di

import com.divarsmartsearch.app.data.repository.ListingRepositoryImpl
import com.divarsmartsearch.app.data.repository.PermanentFilterRepositoryImpl
import com.divarsmartsearch.app.data.repository.SearchRepositoryImpl
import com.divarsmartsearch.app.data.repository.SettingsRepositoryImpl
import com.divarsmartsearch.app.domain.repository.ListingRepository
import com.divarsmartsearch.app.domain.repository.PermanentFilterRepository
import com.divarsmartsearch.app.domain.repository.SearchRepository
import com.divarsmartsearch.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    abstract fun bindListingRepository(impl: ListingRepositoryImpl): ListingRepository

    @Binds
    abstract fun bindPermanentFilterRepository(
        impl: PermanentFilterRepositoryImpl
    ): PermanentFilterRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
