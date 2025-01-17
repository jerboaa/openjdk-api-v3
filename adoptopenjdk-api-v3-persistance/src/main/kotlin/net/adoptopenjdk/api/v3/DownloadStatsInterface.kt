package net.adoptopenjdk.api.v3;

import net.adoptopenjdk.api.v3.dataSources.APIDataStore
import net.adoptopenjdk.api.v3.dataSources.ApiPersistenceFactory
import net.adoptopenjdk.api.v3.models.DbStatsEntry
import net.adoptopenjdk.api.v3.models.DownloadDiff
import net.adoptopenjdk.api.v3.models.DownloadStats
import net.adoptopenjdk.api.v3.models.GithubDownloadStatsDbEntry
import net.adoptopenjdk.api.v3.models.StatsSource
import net.adoptopenjdk.api.v3.models.TotalStats
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

class StatEntry(
        val dateTime: LocalDateTime,
        val count: Long
)

class DownloadStatsInterface {
    private val dataStore = ApiPersistenceFactory.get()

    suspend fun getTrackingStats(days: Int?, source: StatsSource?, featureVersion: Int?, dockerRepo: String?): List<DownloadDiff> {
        //need +1 as for a diff you need num days +1 from db
        val daysSince = (days ?: 30) + 1
        val statsSource = source ?: StatsSource.all
        val since = LocalDateTime.now().minusDays(min(180, daysSince).toLong())

        val stats = getStats(since, featureVersion, dockerRepo, statsSource)

        return calculateDailyDiff(stats)
    }

    private suspend fun getStats(since: LocalDateTime, featureVersion: Int?, dockerRepo: String?, statsSource: StatsSource): Collection<StatEntry> {
        val githubGrouped = getGithubDownloadStatsByDate(since, featureVersion)
        val dockerGrouped = getDockerDownloadStatsByDate(since, dockerRepo)

        val stats = when (statsSource) {
            StatsSource.dockerhub -> dockerGrouped
            StatsSource.github -> githubGrouped
            else -> githubGrouped.union(dockerGrouped)
        }
        return stats
    }

    private fun calculateDailyDiff(stats: Collection<StatEntry>): List<DownloadDiff> {
        return stats
                .groupBy { it.dateTime.toLocalDate() }
                .map { grouped ->
                    StatEntry(
                            grouped.value.map { it.dateTime }.max()!!,
                            grouped.value.map { it.count }.sum()
                    )
                }
                .sortedBy { it.dateTime }
                .windowed(2, 1, false) {
                    val minDiff = max(1, ChronoUnit.MINUTES.between(it[0].dateTime, it[1].dateTime))
                    val downloadDiff = ((it[1].count - it[0].count) * 60L * 24L) / minDiff
                    DownloadDiff(it[1].dateTime, it[1].count, downloadDiff)
                }
    }

    private suspend fun getGithubDownloadStatsByDate(since: LocalDateTime, featureVersion: Int?): List<StatEntry> {
        return sumDailyStats(
                dataStore
                        .getGithubStatsSince(since)
                        .filter { featureVersion == null || it.feature_version == featureVersion }
        )
    }

    private suspend fun getDockerDownloadStatsByDate(since: LocalDateTime, dockerRepo: String?): List<StatEntry> {
        return sumDailyStats(
                dataStore
                        .getDockerStatsSince(since)
                        .filter { dockerRepo == null || it.repo == dockerRepo }
        )
    }

    private fun <T> sumDailyStats(dockerStats: List<DbStatsEntry<T>>): List<StatEntry> {
        return dockerStats
                .groupBy { it.date.toLocalDate() }
                .map { grouped -> StatEntry(getLastDate(grouped.value), formTotalDownloads(grouped.value)) }
    }

    private fun <T> getLastDate(grouped: List<DbStatsEntry<T>>): LocalDateTime {
        return grouped
                .maxBy { it.date }!!
                .date
    }

    private fun <T> formTotalDownloads(stats: List<DbStatsEntry<T>>): Long {
        return stats
                .groupBy { it.getId() }
                .map { grouped -> grouped.value.maxBy { it.date } }
                .map { it!!.getMetric() }
                .sum()
    }


    suspend fun getTotalDownloadStats(): DownloadStats {
        val dockerStats = dataStore.getLatestAllDockerStats()

        val githubStats = getGithubStats()

        val dockerPulls = dockerStats
                .map { it.pulls }
                .sum()

        val githubDownloads = githubStats
                .map { it.downloads }
                .sum()

        val dockerBreakdown = dockerStats
                .map { Pair(it.repo, it.pulls) }
                .toMap()

        val githubBreakdown = githubStats
                .map { Pair(it.feature_version, it.downloads) }
                .toMap()

        val totalStats = TotalStats(dockerPulls, githubDownloads, dockerPulls + githubDownloads)
        return DownloadStats(LocalDateTime.now(), totalStats, githubBreakdown, dockerBreakdown)
    }

    private suspend fun getGithubStats(): List<GithubDownloadStatsDbEntry> {
        return APIDataStore.variants.versions
                .mapNotNull { featureVersion ->
                    dataStore.getLatestGithubStatsForFeatureVersion(featureVersion)
                }
    }
}
