package com.salahlock.app.data.api

import com.salahlock.app.data.api.model.HadithEditionResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface HadithApiService {
    
    // Example: editions/eng-bukhari.json
    @GET("editions/{edition}.json")
    suspend fun getEdition(@Path("edition") editionName: String): HadithEditionResponse
}
