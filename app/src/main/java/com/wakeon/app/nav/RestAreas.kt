package com.wakeon.app.nav

enum class RestAreaType { DROWSY_SHELTER, SERVICE_AREA }

/** 쉼터 하나. [km]는 시연 경로 출발점부터의 거리. 좌표가 있으면 내비 경유지로 넣는다. */
data class RestArea(
    val name: String,
    val type: RestAreaType,
    val km: Double,
    val exit: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * 시연 경로 (DEMO_SCENARIO 4장). 경부고속도로를 단순화한 1차원 경로.
 * 실제 공공데이터 연동은 2차.
 */
data class DemoRoute(
    val name: String,
    val totalKm: Double,
    val restAreas: List<RestArea>,
) {
    /** [positionKm]보다 앞에 있는 쉼터(가까운 순). */
    fun ahead(positionKm: Double): List<RestArea> =
        restAreas.filter { it.km > positionKm }.sortedBy { it.km }

    /**
     * 쉼터 제안 후보 (F8): 탐색 거리 안의 졸음쉼터 우선, 없으면 휴게소.
     * 반환: (제안할 곳, 다음 후보)
     */
    fun offer(positionKm: Double, searchKm: Double): Pair<RestArea, RestArea?>? {
        val inRange = ahead(positionKm).filter { it.km - positionKm <= searchKm }
        val chosen = inRange.firstOrNull { it.type == RestAreaType.DROWSY_SHELTER }
            ?: inRange.firstOrNull()
            ?: return null
        val next = ahead(positionKm).firstOrNull { it != chosen && it.km > positionKm }
        return chosen to next
    }

    /** 출발 안내(S-16)의 다음 쉼터: 졸음쉼터 우선, [maxKm]보다 멀면 휴게소. */
    fun nextAfter(positionKm: Double, maxKm: Double): RestArea? {
        val ahead = ahead(positionKm)
        return ahead.firstOrNull { it.type == RestAreaType.DROWSY_SHELTER && it.km - positionKm <= maxKm }
            ?: ahead.firstOrNull { it.type == RestAreaType.SERVICE_AREA }
            ?: ahead.firstOrNull()
    }
}
