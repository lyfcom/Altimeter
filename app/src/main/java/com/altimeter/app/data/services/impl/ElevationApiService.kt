package com.altimeter.app.data.services.impl

import com.altimeter.app.data.models.AltitudeData
import com.altimeter.app.data.models.AltitudeSource
import com.altimeter.app.data.models.LocationInfo
import com.altimeter.app.data.services.AltitudeService
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDateTime

/**
 * 海拔API服务实现 - 使用Open Elevation API
 */
class ElevationApiService : AltitudeService {
    
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.open-elevation.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val api = retrofit.create(ElevationApi::class.java)
    
    override suspend fun getAltitude(location: LocationInfo): Result<AltitudeData> {
        return try {
            withContext(Dispatchers.IO) {
                val response = api.getElevation(
                    locations = "${location.latitude},${location.longitude}"
                )
                
                if (response.results.isNotEmpty()) {
                    val result = response.results[0]
                    val reliability = calculateApiReliability(result.elevation)
                    
                    val altitudeData = AltitudeData(
                        altitude = result.elevation,
                        source = AltitudeSource.ELEVATION_API,
                        accuracy = AltitudeSource.ELEVATION_API.typicalAccuracy,
                        reliability = reliability,
                        timestamp = LocalDateTime.now(),
                        latitude = location.latitude,
                        longitude = location.longitude,
                        description = "Open Elevation API获取的海拔数据"
                    )
                    
                    Result.success(altitudeData)
                } else {
                    Result.failure(Exception("API未返回海拔数据"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getServiceName(): String = "海拔API服务"
    
    override suspend fun isAvailable(): Boolean {
        return try {
            // 简单的连通性测试
            withContext(Dispatchers.IO) {
                val response = api.getElevation("0,0")
                response.results.isNotEmpty()
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 计算API可靠度
     */
    private fun calculateApiReliability(elevation: Double): Double {
        val source = AltitudeSource.ELEVATION_API
        return when {
            elevation > -500 && elevation < 9000 -> source.baseReliability + 5  // 正常海拔范围
            elevation >= -500 && elevation <= 11000 -> source.baseReliability   // 扩展范围
            else -> source.baseReliability - 10                                 // 异常海拔
        }.coerceIn(0.0, 100.0)
    }
}

/**
 * Open Elevation API接口
 */
interface ElevationApi {
    
    @GET("api/v1/lookup")
    suspend fun getElevation(
        @Query("locations") locations: String
    ): ElevationResponse
}

/**
 * API响应数据类
 */
data class ElevationResponse(
    @SerializedName("results")
    val results: List<ElevationResult>
)

data class ElevationResult(
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("elevation")
    val elevation: Double
)