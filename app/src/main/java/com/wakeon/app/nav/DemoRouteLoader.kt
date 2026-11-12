package com.wakeon.app.nav

import android.content.Context
import org.json.JSONObject

/** `assets/demo_route.json`을 읽는다(DEMO_SCENARIO 4장). 좌표(lat, lng)는 있으면 경유지 추가에 쓴다. */
object DemoRouteLoader {
    fun load(context: Context, asset: String = "demo_route.json"): DemoRoute {
        val json = JSONObject(context.assets.open(asset).bufferedReader().use { it.readText() })
        val list = json.getJSONArray("restAreas")
        val areas = (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            RestArea(
                name = o.getString("name"),
                type = if (o.getString("type") == "service_area") RestAreaType.SERVICE_AREA else RestAreaType.DROWSY_SHELTER,
                km = o.getDouble("km"),
                exit = o.optString("exit", "오른쪽"),
                latitude = o.optDouble("lat").takeIf { !it.isNaN() },
                longitude = o.optDouble("lng").takeIf { !it.isNaN() },
            )
        }
        return DemoRoute(json.getString("route"), json.getDouble("totalKm"), areas)
    }
}
