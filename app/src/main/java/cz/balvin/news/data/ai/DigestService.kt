package cz.balvin.news.data.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import cz.balvin.news.data.local.ArticleListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Turns an edition into the handful of themes it is actually about. */
interface DigestService {

    sealed interface Result {
        data class Success(val digest: Digest) : Result
        data object NoApiKey : Result
        data class Failure(val message: String) : Result
    }

    suspend fun summarise(articles: List<ArticleListItem>, apiKey: String): Result
}

class AnthropicDigestService(
    private val now: () -> Long = System::currentTimeMillis,
) : DigestService {

    override suspend fun summarise(
        articles: List<ArticleListItem>,
        apiKey: String,
    ): DigestService.Result = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext DigestService.Result.NoApiKey
        if (articles.isEmpty()) {
            return@withContext DigestService.Result.Success(Digest(emptyList(), now()))
        }

        try {
            val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                .outputConfig(DigestPayload::class.java)
                .addUserMessage(articles.joinToString("\n\n", transform = ::render))
                .build()

            val payload = client.messages().create(params).content()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .firstOrNull()
                ?: return@withContext DigestService.Result.Failure("Empty response")

            DigestService.Result.Success(
                Digest(
                    topics = payload.topics.map {
                        DigestTopic(
                            title = it.title.trim(),
                            summary = it.summary.trim(),
                            sources = it.sources.filter(String::isNotBlank),
                        )
                    }.filter { it.title.isNotBlank() },
                    generatedAt = now(),
                )
            )
        } catch (e: Exception) {
            // A digest that cannot be produced must not take the edition down with
            // it; the articles are already on screen.
            DigestService.Result.Failure(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun render(item: ArticleListItem): String = buildString {
        append(item.feedTitle).append(" | ").append(item.article.title)
        item.article.summary.takeIf(String::isNotBlank)?.let { append("\n").append(it) }
    }

    private companion object {
        const val MODEL = "claude-opus-5"
        const val MAX_TOKENS = 4_000L

        val SYSTEM_PROMPT = """
            Dostaneš seznam zpráv z jednoho dne, jednu na odstavec, ve formátu
            "zdroj | titulek" a volitelně perex.

            Vrať témata, o která ve skutečnosti jde — ne převyprávěné titulky.
            Řiď se tím:

            - Nejvýše šest témat, seřazená podle důležitosti. Když je den chudý,
              vrať jich méně; tři dobrá témata jsou lepší než šest vycpaných.
            - Zprávy o téže věci slučuj do jednoho tématu, i když je každý zdroj
              podal jinak.
            - Shrnutí piš jako dvě až tři věty, které čtenáři stačí místo článků.
              Uveď, co se stalo a proč na tom záleží. Žádné návodné fráze typu
              "v článku se dočtete".
            - U každého tématu vyjmenuj zdroje, které ho pokryly, přesně tak, jak
              jsou pojmenované ve vstupu.
            - Piš česky, věcně, bez nadsázky a bez hodnocení.
            - Když si vstupem nejsi jistý, radši téma vynech, než abys domýšlel.
        """.trimIndent()
    }
}
