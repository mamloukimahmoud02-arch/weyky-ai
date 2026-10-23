package com.example.device.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HourlyForecastItem(
  val timeIso: String,
  val hourLabel: String,
  val temperature: Float,
  val precipitationProbability: Int,
  val precipitationMm: Float,
  val weatherCode: Int,
  val condition: String,
  val windSpeedKmH: Float,
  val uvIndex: Float?
)

data class DailyForecastItem(
  val dateIso: String,
  val dayLabel: String,
  val maxTemp: Float,
  val minTemp: Float,
  val precipitationProbabilityMax: Int,
  val precipitationSumMm: Float,
  val weatherCode: Int,
  val condition: String,
  val maxWindSpeedKmH: Float,
  val maxWindGustsKmH: Float?,
  val maxUvIndex: Float?,
  val sunrise: String?,
  val sunset: String?
)

data class WeatherSnapshot(
  // Current conditions
  val temperatureCelsius: Float?,
  val apparentTemperatureCelsius: Float? = null,
  val condition: String?,
  val weatherCode: Int? = null,
  val humidityPercent: Int? = null,
  val precipitationMm: Float? = null,
  val windSpeedKmH: Float? = null,
  val windDirectionDegrees: Float? = null,
  val windDirectionCompass: String? = null,
  val windGustsKmH: Float? = null,
  val pressureHpa: Float? = null,
  val cloudCoverPercent: Int? = null,
  val uvIndex: Float? = null,
  val airQualitySummary: String? = null,

  // Today & Tomorrow quick summaries
  val maxTempToday: Float? = null,
  val minTempToday: Float? = null,
  val rainProbTodayMax: Int? = null,
  val sunriseToday: String? = null,
  val sunsetToday: String? = null,
  val maxTempTomorrow: Float? = null,
  val minTempTomorrow: Float? = null,
  val rainProbTomorrowMax: Int? = null,
  val conditionTomorrow: String? = null,
  val windSpeedTomorrowMax: Float? = null,

  // Detailed lists
  val hourlyForecast: List<HourlyForecastItem> = emptyList(),
  val dailyForecast: List<DailyForecastItem> = emptyList(),

  // Metadata
  val locationName: String? = null,
  val isAvailable: Boolean = true,
  val isCached: Boolean = false,
  val statusMessage: String? = null,
  val lastUpdatedMillis: Long = System.currentTimeMillis()
) {
  val formattedSummary: String
    get() = when {
      !isAvailable -> statusMessage ?: "الطقس غير متاح"
      temperatureCelsius != null && condition != null -> {
        val temp = String.format(Locale.US, "%.0f", temperatureCelsius)
        val feels = apparentTemperatureCelsius?.let { " (المحسوسة ${String.format(Locale.US, "%.0f", it)}°)" } ?: ""
        val cacheNote = if (isCached) " [مخزن]" else ""
        "$condition - ${temp}°م$feels$cacheNote"
      }
      else -> statusMessage ?: "الطقس غير متاح"
    }

  val lastUpdatedFormatted: String
    get() {
      val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
      return sdf.format(Date(lastUpdatedMillis))
    }
}

interface WeatherProvider {
  suspend fun fetchWeather(): WeatherSnapshot
  fun getCachedWeather(): WeatherSnapshot?
}

class RealWeatherProvider(
  private val locationProvider: LocationProvider
) : WeatherProvider {

  @Volatile
  private var cachedWeather: WeatherSnapshot? = null

  @Volatile
  private var cachedCoordKey: String? = null

  @Volatile
  private var cachedAreaName: String? = null

  override fun getCachedWeather(): WeatherSnapshot? = cachedWeather

  override suspend fun fetchWeather(): WeatherSnapshot = withContext(Dispatchers.IO) {
    val locResult = locationProvider.getCurrentLocation()
    val coords = when (locResult) {
      is LocationResult.PermissionRequired -> {
        return@withContext cachedWeather?.copy(isCached = true)
          ?: WeatherSnapshot(
            temperatureCelsius = null,
            condition = null,
            locationName = null,
            isAvailable = false,
            statusMessage = "الطقس: يحتاج صلاحية الموقع"
          )
      }
      is LocationResult.LocationDisabledOrUnavailable -> {
        return@withContext cachedWeather?.copy(isCached = true)
          ?: WeatherSnapshot(
            temperatureCelsius = null,
            condition = null,
            locationName = null,
            isAvailable = false,
            statusMessage = "الطقس: الموقع غير متاح"
          )
      }
      is LocationResult.Success -> locResult.location
    }

    var connection: HttpURLConnection? = null
    try {
      val lat = String.format(Locale.US, "%.6f", coords.latitude)
      val lon = String.format(Locale.US, "%.6f", coords.longitude)
      val exactAreaName = resolveExactAreaName(coords)

      val urlStr = "https://api.open-meteo.com/v1/forecast" +
        "?latitude=$lat&longitude=$lon" +
        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,rain,weather_code,cloud_cover,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
        "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m,uv_index" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max,uv_index_max,sunrise,sunset" +
        "&models=best_match&timezone=auto"

      val url = URL(urlStr)
      connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 4500
        readTimeout = 4500
        requestMethod = "GET"
      }

      if (connection.responseCode == 200) {
        val reader = BufferedReader(InputStreamReader(connection.inputStream))
        val response = reader.readText()
        reader.close()

        val json = JSONObject(response)
        val freshSnapshot = parseWeatherJson(json, exactAreaName)
        cachedWeather = freshSnapshot
        freshSnapshot
      } else {
        cachedWeather?.copy(isCached = true)
          ?: WeatherSnapshot(
            temperatureCelsius = null,
            condition = null,
            locationName = null,
            isAvailable = false,
            statusMessage = "تعذر الاتصال بخدمة الطقس (كود ${connection.responseCode})."
          )
      }
    } catch (_: Exception) {
      cachedWeather?.copy(isCached = true)
        ?: WeatherSnapshot(
          temperatureCelsius = null,
          condition = null,
          locationName = null,
          isAvailable = false,
          statusMessage = "الطقس غير متصل حالياً"
        )
    } finally {
      connection?.disconnect()
    }
  }

  private fun parseWeatherJson(json: JSONObject, resolvedAreaName: String): WeatherSnapshot {
    val current = json.getJSONObject("current")
    val temp = current.optDouble("temperature_2m", Double.NaN).toFloat()
    val feelsLike = current.optDouble("apparent_temperature", Double.NaN).let { if (it.isNaN()) null else it.toFloat() }
    val humidity = current.optInt("relative_humidity_2m", -1).let { if (it >= 0) it else null }
    val precip = current.optDouble("precipitation", 0.0).toFloat()
    val weatherCode = current.optInt("weather_code", 0)
    val cloudCover = current.optInt("cloud_cover", -1).let { if (it >= 0) it else null }
    val pressure = current.optDouble("surface_pressure", Double.NaN).let { if (it.isNaN()) null else it.toFloat() }
    val windSpeed = current.optDouble("wind_speed_10m", 0.0).toFloat()
    val windDirection = current.optDouble("wind_direction_10m", 0.0).toFloat()
    val windGusts = current.optDouble("wind_gusts_10m", Double.NaN).let { if (it.isNaN()) null else it.toFloat() }

    val condition = mapWeatherCode(weatherCode)
    val windCompass = degreesToCompass(windDirection)

    // Parse Hourly
    val hourlyList = mutableListOf<HourlyForecastItem>()
    if (json.has("hourly")) {
      val hourly = json.getJSONObject("hourly")
      val times = hourly.optJSONArray("time")
      val temps = hourly.optJSONArray("temperature_2m")
      val probs = hourly.optJSONArray("precipitation_probability")
      val precips = hourly.optJSONArray("precipitation")
      val codes = hourly.optJSONArray("weather_code")
      val winds = hourly.optJSONArray("wind_speed_10m")
      val uvs = hourly.optJSONArray("uv_index")

      if (times != null && temps != null) {
        val nowCal = Calendar.getInstance()
        val currentHourStr = SimpleDateFormat("yyyy-MM-dd'T'HH", Locale.US).format(nowCal.time)
        var startIndex = 0
        for (i in 0 until times.length()) {
          val t = times.optString(i)
          if (t.startsWith(currentHourStr)) {
            startIndex = i
            break
          }
        }
        // Take next 24 hours
        val count = minOf(24, times.length() - startIndex)
        for (i in startIndex until (startIndex + count)) {
          val iso = times.optString(i)
          val hourLabel = iso.substringAfter("T", "")
          val hTemp = temps.optDouble(i, 0.0).toFloat()
          val hProb = probs?.optInt(i, 0) ?: 0
          val hPrecip = precips?.optDouble(i, 0.0)?.toFloat() ?: 0f
          val hCode = codes?.optInt(i, 0) ?: 0
          val hWind = winds?.optDouble(i, 0.0)?.toFloat() ?: 0f
          val hUv = uvs?.optDouble(i, Double.NaN)?.let { if (it.isNaN()) null else it.toFloat() }

          hourlyList.add(
            HourlyForecastItem(
              timeIso = iso,
              hourLabel = hourLabel,
              temperature = hTemp,
              precipitationProbability = hProb,
              precipitationMm = hPrecip,
              weatherCode = hCode,
              condition = mapWeatherCode(hCode),
              windSpeedKmH = hWind,
              uvIndex = hUv
            )
          )
        }
      }
    }

    // Parse Daily
    val dailyList = mutableListOf<DailyForecastItem>()
    var maxTempToday: Float? = null
    var minTempToday: Float? = null
    var rainProbToday: Int? = null
    var sunriseToday: String? = null
    var sunsetToday: String? = null
    var maxTempTomorrow: Float? = null
    var minTempTomorrow: Float? = null
    var rainProbTomorrow: Int? = null
    var conditionTomorrow: String? = null
    var windSpeedTomorrow: Float? = null

    if (json.has("daily")) {
      val daily = json.getJSONObject("daily")
      val dTimes = daily.optJSONArray("time")
      val dCodes = daily.optJSONArray("weather_code")
      val dMaxTemps = daily.optJSONArray("temperature_2m_max")
      val dMinTemps = daily.optJSONArray("temperature_2m_min")
      val dRainProbs = daily.optJSONArray("precipitation_probability_max")
      val dPrecipSums = daily.optJSONArray("precipitation_sum")
      val dWindSpeeds = daily.optJSONArray("wind_speed_10m_max")
      val dWindGusts = daily.optJSONArray("wind_gusts_10m_max")
      val dUvs = daily.optJSONArray("uv_index_max")
      val dSunrises = daily.optJSONArray("sunrise")
      val dSunsets = daily.optJSONArray("sunset")

      if (dTimes != null) {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdfDate.format(Date())
        val calTom = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowStr = sdfDate.format(calTom.time)

        for (i in 0 until minOf(7, dTimes.length())) {
          val dIso = dTimes.optString(i)
          val dCode = dCodes?.optInt(i, 0) ?: 0
          val maxT = dMaxTemps?.optDouble(i, 0.0)?.toFloat() ?: 0f
          val minT = dMinTemps?.optDouble(i, 0.0)?.toFloat() ?: 0f
          val rainP = dRainProbs?.optInt(i, 0) ?: 0
          val precipS = dPrecipSums?.optDouble(i, 0.0)?.toFloat() ?: 0f
          val windS = dWindSpeeds?.optDouble(i, 0.0)?.toFloat() ?: 0f
          val windG = dWindGusts?.optDouble(i, Double.NaN)?.let { if (it.isNaN()) null else it.toFloat() }
          val uvM = dUvs?.optDouble(i, Double.NaN)?.let { if (it.isNaN()) null else it.toFloat() }
          val sRise = dSunrises?.optString(i)?.substringAfter("T")
          val sSet = dSunsets?.optString(i)?.substringAfter("T")

          val dayLabel = when (dIso) {
            todayStr -> "اليوم"
            tomorrowStr -> "غداً"
            else -> getArabicDayOfWeek(dIso)
          }

          if (dIso == todayStr) {
            maxTempToday = maxT
            minTempToday = minT
            rainProbToday = rainP
            sunriseToday = sRise
            sunsetToday = sSet
          } else if (dIso == tomorrowStr) {
            maxTempTomorrow = maxT
            minTempTomorrow = minT
            rainProbTomorrow = rainP
            conditionTomorrow = mapWeatherCode(dCode)
            windSpeedTomorrow = windS
          }

          dailyList.add(
            DailyForecastItem(
              dateIso = dIso,
              dayLabel = dayLabel,
              maxTemp = maxT,
              minTemp = minT,
              precipitationProbabilityMax = rainP,
              precipitationSumMm = precipS,
              weatherCode = dCode,
              condition = mapWeatherCode(dCode),
              maxWindSpeedKmH = windS,
              maxWindGustsKmH = windG,
              maxUvIndex = uvM,
              sunrise = sRise,
              sunset = sSet
            )
          )
        }
      }
    }

    // Current UV from nearest hourly item
    val currentUv = hourlyList.firstOrNull()?.uvIndex ?: dailyList.firstOrNull()?.maxUvIndex

    return WeatherSnapshot(
      temperatureCelsius = if (temp.isNaN()) null else temp,
      apparentTemperatureCelsius = feelsLike,
      condition = condition,
      weatherCode = weatherCode,
      humidityPercent = humidity,
      precipitationMm = precip,
      windSpeedKmH = windSpeed,
      windDirectionDegrees = windDirection,
      windDirectionCompass = windCompass,
      windGustsKmH = windGusts,
      pressureHpa = pressure,
      cloudCoverPercent = cloudCover,
      uvIndex = currentUv,
      airQualitySummary = null, // Transparently indicate when direct AQI sensor isn't bundled
      maxTempToday = maxTempToday,
      minTempToday = minTempToday,
      rainProbTodayMax = rainProbToday,
      sunriseToday = sunriseToday,
      sunsetToday = sunsetToday,
      maxTempTomorrow = maxTempTomorrow,
      minTempTomorrow = minTempTomorrow,
      rainProbTomorrowMax = rainProbTomorrow,
      conditionTomorrow = conditionTomorrow,
      windSpeedTomorrowMax = windSpeedTomorrow,
      hourlyForecast = hourlyList,
      dailyForecast = dailyList,
      locationName = resolvedAreaName,
      isAvailable = true,
      isCached = false,
      lastUpdatedMillis = System.currentTimeMillis()
    )
  }

  /**
   * Resolves the exact geographical area (القرية / البلدية / المدينة + الولاية)
   * using high-detail Arabic Reverse Geocoding (OpenStreetMap Nominatim + BigDataCloud + Android Geocoder).
   */
  private fun resolveExactAreaName(coords: DeviceLocation): String {
    val coordKey = String.format(Locale.US, "%.3f,%.3f", coords.latitude, coords.longitude)
    val existing = cachedAreaName
    if (cachedCoordKey == coordKey && !existing.isNullOrBlank()) {
      return existing
    }

    // 1. Try OpenStreetMap Nominatim Reverse Geocoding at zoom=18 (village / suburb / municipality / wilaya)
    val nominatimName = fetchNominatimAreaName(coords.latitude, coords.longitude)
    if (!nominatimName.isNullOrBlank()) {
      cachedCoordKey = coordKey
      cachedAreaName = nominatimName
      return nominatimName
    }

    // 2. Try BigDataCloud Client Reverse Geocoding in Arabic
    val bdcName = fetchBigDataCloudAreaName(coords.latitude, coords.longitude)
    if (!bdcName.isNullOrBlank()) {
      cachedCoordKey = coordKey
      cachedAreaName = bdcName
      return bdcName
    }

    // 3. Fallback to Android on-device Geocoder
    val localGeocoder = coords.resolvedLocationName?.takeIf { it.isNotBlank() }
    if (localGeocoder != null) {
      cachedCoordKey = coordKey
      cachedAreaName = localGeocoder
      return localGeocoder
    }

    return cachedWeather?.locationName?.takeIf { it.isNotBlank() && it != "موقعي الحالي" } ?: "موقعي الحالي"
  }

  private fun fetchNominatimAreaName(lat: Double, lon: Double): String? {
    var conn: HttpURLConnection? = null
    return try {
      val latStr = String.format(Locale.US, "%.6f", lat)
      val lonStr = String.format(Locale.US, "%.6f", lon)
      val url = URL(
        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$latStr&lon=$lonStr&zoom=18&accept-language=ar&addressdetails=1"
      )
      conn = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 2800
        readTimeout = 2800
        requestMethod = "GET"
        setRequestProperty("User-Agent", "WeykyAssistantLauncher/1.0 (Android)")
        setRequestProperty("Accept-Language", "ar")
      }
      if (conn.responseCode == 200) {
        val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        val root = JSONObject(text)
        val addr = root.optJSONObject("address") ?: return null

        val finePlace = firstNonBlank(
          addr.optString("village"),
          addr.optString("hamlet"),
          addr.optString("town"),
          addr.optString("suburb"),
          addr.optString("neighbourhood"),
          addr.optString("quarter"),
          addr.optString("municipality"),
          addr.optString("city_district"),
          addr.optString("city"),
          addr.optString("county")
        )
        val statePlace = firstNonBlank(
          addr.optString("state"),
          addr.optString("province"),
          addr.optString("region"),
          addr.optString("city")
        )

        formatPlaceAndState(finePlace, statePlace)
      } else null
    } catch (_: Exception) {
      null
    } finally {
      conn?.disconnect()
    }
  }

  private fun fetchBigDataCloudAreaName(lat: Double, lon: Double): String? {
    var conn: HttpURLConnection? = null
    return try {
      val latStr = String.format(Locale.US, "%.6f", lat)
      val lonStr = String.format(Locale.US, "%.6f", lon)
      val url = URL(
        "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$latStr&longitude=$lonStr&localityLanguage=ar"
      )
      conn = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 2800
        readTimeout = 2800
        requestMethod = "GET"
      }
      if (conn.responseCode == 200) {
        val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        val root = JSONObject(text)
        val finePlace = firstNonBlank(
          root.optString("locality"),
          root.optString("city")
        )
        val statePlace = firstNonBlank(
          root.optString("principalSubdivision"),
          root.optString("city")
        )
        formatPlaceAndState(finePlace, statePlace)
      } else null
    } catch (_: Exception) {
      null
    } finally {
      conn?.disconnect()
    }
  }

  private fun firstNonBlank(vararg values: String?): String? {
    for (v in values) {
      val clean = v?.trim()
      if (!clean.isNullOrEmpty() && clean != "null") {
        return clean
      }
    }
    return null
  }

  private fun formatPlaceAndState(finePlace: String?, statePlace: String?): String? {
    return when {
      finePlace != null && statePlace != null &&
        !finePlace.equals(statePlace, ignoreCase = true) &&
        !statePlace.contains(finePlace) && !finePlace.contains(statePlace) ->
        "$finePlace، $statePlace"
      finePlace != null -> finePlace
      statePlace != null -> statePlace
      else -> null
    }
  }

  private fun degreesToCompass(deg: Float): String {
    val d = (deg % 360 + 360) % 360
    return when {
      d >= 337.5 || d < 22.5 -> "شمالية"
      d in 22.5..67.5 -> "شمالية شرقية"
      d in 67.5..112.5 -> "شرقية"
      d in 112.5..157.5 -> "جنوبية شرقية"
      d in 157.5..202.5 -> "جنوبية"
      d in 202.5..247.5 -> "جنوبية غربية"
      d in 247.5..292.5 -> "غربية"
      d in 292.5..337.5 -> "شمالية غربية"
      else -> "متقلبة"
    }
  }

  private fun getArabicDayOfWeek(dateIso: String): String {
    return try {
      val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateIso) ?: return dateIso
      val cal = Calendar.getInstance().apply { time = date }
      when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY -> "السبت"
        Calendar.SUNDAY -> "الأحد"
        Calendar.MONDAY -> "الاثنين"
        Calendar.TUESDAY -> "الثلاثاء"
        Calendar.WEDNESDAY -> "الأربعاء"
        Calendar.THURSDAY -> "الخميس"
        Calendar.FRIDAY -> "الجمعة"
        else -> dateIso
      }
    } catch (_: Exception) {
      dateIso
    }
  }

  private fun mapWeatherCode(code: Int): String {
    return when (code) {
      0 -> "سماء صافية"
      1 -> "غائم جزئياً"
      2 -> "غائم"
      3 -> "غائم كلياً"
      45 -> "ضباب"
      48 -> "ضباب كثيف"
      51 -> "رذاذ خفيف"
      53 -> "رذاذ معتدل"
      55 -> "رذاذ كثيف"
      61 -> "مطر خفيف"
      63 -> "مطر معتدل"
      65 -> "مطر غزير"
      71 -> "ثلوج خفيفة"
      73 -> "ثلوج معتدلة"
      75 -> "ثلوج كثيفة"
      80 -> "زخات مطر خفيفة"
      81 -> "زخات مطر معتدلة"
      82 -> "زخات مطر غزيرة"
      95 -> "عاصفة رعدية"
      96, 99 -> "عاصفة رعدية قوية"
      else -> "طقس متقلب"
    }
  }
}
