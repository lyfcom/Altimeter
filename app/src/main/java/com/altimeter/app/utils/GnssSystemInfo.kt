package com.altimeter.app.utils

/**
 * GNSS系统信息工具类
 * 提供全球导航卫星系统的相关信息
 */
object GnssSystemInfo {
    
    /**
     * 支持的GNSS系统信息
     */
    data class GnssSystem(
        val name: String,           // 系统名称
        val country: String,        // 运营国家/地区
        val description: String,    // 系统描述
        val constellation: String   // 星座名称
    )
    
    /**
     * 当前支持的GNSS系统列表
     */
    val supportedSystems = listOf(
        GnssSystem(
            name = "GPS",
            country = "美国",
            description = "全球定位系统，最早也是最广泛使用的GNSS系统",
            constellation = "GPS"
        ),
        GnssSystem(
            name = "北斗",
            country = "中国",
            description = "中国自主建设的全球卫星导航系统",
            constellation = "BeiDou"
        ),
        GnssSystem(
            name = "GLONASS",
            country = "俄罗斯",
            description = "俄罗斯的全球导航卫星系统",
            constellation = "GLONASS"
        ),
        GnssSystem(
            name = "Galileo",
            country = "欧盟",
            description = "欧盟开发的全球导航卫星系统",
            constellation = "Galileo"
        ),
        GnssSystem(
            name = "QZSS",
            country = "日本",
            description = "日本的准天顶卫星系统，主要覆盖亚太地区",
            constellation = "QZSS"
        ),
        GnssSystem(
            name = "IRNSS/NavIC",
            country = "印度",
            description = "印度的区域导航卫星系统",
            constellation = "IRNSS"
        )
    )
    
    /**
     * 获取支持说明文本
     */
    fun getSupportDescription(): String {
        return "本应用使用Android的融合定位服务，自动支持设备上可用的所有GNSS系统，" +
               "包括${supportedSystems.take(4).joinToString("、") { it.name }}等主要系统。" +
               "具体可用的卫星系统取决于您的设备硬件支持和当前地理位置。"
    }
    
    /**
     * 获取主要系统名称列表
     */
    fun getMainSystemNames(): List<String> {
        return supportedSystems.take(4).map { it.name }
    }
    
    /**
     * 获取系统详细信息
     */
    fun getSystemDetails(): String {
        return supportedSystems.joinToString("\n\n") { system ->
            "• ${system.name}（${system.country}）: ${system.description}"
        }
    }
}