package com.salahlock.app.data.api.model

import com.google.gson.annotations.SerializedName

data class HadithEditionResponse(
    @SerializedName("metadata") val metadata: HadithMetadata,
    @SerializedName("hadiths") val hadiths: List<HadithItem>
)

data class HadithMetadata(
    @SerializedName("name") val name: String,
    @SerializedName("sections") val sections: Map<String, String>
)

data class HadithItem(
    @SerializedName("hadithnumber") val hadithNumber: Double,
    @SerializedName("arabicnumber") val arabicNumber: Double,
    @SerializedName("text") val text: String,
    @SerializedName("reference") val reference: HadithReference?
)

data class HadithReference(
    @SerializedName("book") val book: Int,
    @SerializedName("hadith") val hadith: Int
)
