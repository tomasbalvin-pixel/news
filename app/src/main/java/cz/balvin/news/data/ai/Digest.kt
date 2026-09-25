package cz.balvin.news.data.ai

/** One theme the edition is actually about, with the sources that covered it. */
data class DigestTopic(
    val title: String,
    val summary: String,
    val sources: List<String>,
)

data class Digest(
    val topics: List<DigestTopic>,
    val generatedAt: Long,
)

/**
 * What the model is asked to fill in. Plain mutable properties with a no-argument
 * constructor, so Jackson binds them without the Kotlin module and the SDK can
 * derive the schema from the getters.
 */
class DigestPayload {
    var topics: List<TopicPayload> = emptyList()
}

class TopicPayload {
    var title: String = ""
    var summary: String = ""
    var sources: List<String> = emptyList()
}
