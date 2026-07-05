package com.example.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.remote.GeminiApiService
import com.example.data.repository.AiRepository
import com.example.data.repository.DocumentRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ServiceLocator {
    private var database: AppDatabase? = null
    private var documentRepository: DocumentRepository? = null
    private var aiRepository: AiRepository? = null
    private var geminiApiService: GeminiApiService? = null

    private fun getDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            val db = AppDatabase.getDatabase(context)
            database = db
            db
        }
    }

    private fun getGeminiApiService(): GeminiApiService {
        return geminiApiService ?: synchronized(this) {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://generativelanguage.googleapis.com/")
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            val service = retrofit.create(GeminiApiService::class.java)
            geminiApiService = service
            service
        }
    }

    fun provideDocumentRepository(context: Context): DocumentRepository {
        return documentRepository ?: synchronized(this) {
            val db = getDatabase(context)
            val repo = DocumentRepository(context.applicationContext, db.recentFileDao())
            documentRepository = repo
            repo
        }
    }

    fun provideAiRepository(context: Context): AiRepository {
        return aiRepository ?: synchronized(this) {
            val db = getDatabase(context)
            val api = getGeminiApiService()
            val repo = AiRepository(context.applicationContext, api, db.recentFileDao())
            aiRepository = repo
            repo
        }
    }
}
