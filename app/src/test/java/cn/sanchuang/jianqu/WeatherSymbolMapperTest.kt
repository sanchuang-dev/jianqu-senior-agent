package cn.sanchuang.jianqu

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherSymbolMapperTest {
    @Test
    fun mapsKnownMetSymbolsBySemanticPriority() {
        val cases = mapOf(
            "rainshowersandthunder_day" to "雷阵雨",
            "heavysnow" to "雪",
            "sleetshowers_night" to "雨夹雪",
            "fog" to "雾",
            "lightrain" to "雨",
            "partlycloudy_day" to "多云间晴",
            "cloudy" to "多云",
            "fair_day" to "晴间多云",
            "clearsky_night" to "晴"
        )

        cases.forEach { (symbol, expected) ->
            assertEquals(symbol, expected, WeatherSymbolMapper.describe(symbol))
        }
    }

    @Test
    fun isCaseInsensitiveAndFallsBackForUnknownSymbols() {
        assertEquals("雨", WeatherSymbolMapper.describe("LIGHTRAIN"))
        assertEquals("预报中", WeatherSymbolMapper.describe("future-provider-symbol"))
        assertEquals("预报中", WeatherSymbolMapper.describe(""))
    }
}
