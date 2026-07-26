package com.nova.cfquota.core

object Constants {
    const val GRAPHQL_ENDPOINT = "https://api.cloudflare.com/client/v4/graphql"
    const val DEFAULT_DAILY_QUOTA = 100_000L

    // Reset happens every day at 08:00 Beijing time (UTC+8)
    const val RESET_HOUR_BEIJING = 8
    const val BEIJING_ZONE_ID = "Asia/Shanghai"

    // Background auto-refresh interval bounds (minutes). WorkManager enforces a
    // 15-minute floor for periodic work, which matches our minimum.
    const val MIN_REFRESH_INTERVAL_MIN = 15
    const val DEFAULT_REFRESH_INTERVAL_MIN = 15
}
