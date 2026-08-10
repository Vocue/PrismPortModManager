package com.prismport.modmanager.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class ModSearchResult(val hits: List<ModHit>)

data class ModHit(
    @SerializedName("project_id") val projectId: String,
    val title: String,
    val description: String,
    val author: String
)

data class ModVersion(
    val id: String,
    val name: String,
    val files: List<ModFile>
)

data class ModFile(
    val url: String,
    val filename: String,
    val primary: Boolean
)

interface ModrinthService {
    @GET("search")
    suspend fun searchMods(
        @Query("query") query: String,
        @Query("facets") facets: String? = null
    ): ModSearchResult

    @GET("project/{id}/version")
    suspend fun getProjectVersions(
        @Path("id") projectId: String,
        @Query("loaders") loaders: String? = null,
        @Query("game_versions") gameVersions: String? = null
    ): List<ModVersion>
}

object ModrinthClient {
    val service: ModrinthService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.modrinth.com/v2/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ModrinthService::class.java)
    }
}
