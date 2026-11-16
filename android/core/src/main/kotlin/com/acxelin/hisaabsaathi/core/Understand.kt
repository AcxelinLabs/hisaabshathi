package com.acxelin.hisaabsaathi.core

/** What one utterance turned out to be. */
sealed interface Understood {
    /** Ledger lines to confirm. */
    data class Entries(val extraction: Extraction) : Understood

    /** A question about the ledger, answered from the worker's own records. */
    data class Ask(val query: Query) : Understood

    /** A greeting or simple question with a fixed answer. */
    data class Talk(val chat: Chat) : Understood

    /** None of the above: the language model may try, otherwise the app explains what it can do. */
    data class Unknown(val transcript: String) : Understood
}

/** The order in which the app tries to make sense of an utterance, with no model involved. */
object Understand {
    fun of(transcript: String): Understood {
        val rules = RuleBasedExtractor.extract(transcript)
        rules.query?.let {
            // "What is the date today?" mentions today but asks nothing about money.
            if (it.topic == QueryTopic.ALL && SmallTalk.detect(transcript) == Chat.DATE) return Understood.Talk(Chat.DATE)
            return Understood.Ask(it)
        }
        if (rules.entries.isNotEmpty()) return Understood.Entries(rules)
        SmallTalk.detect(transcript)?.let { return Understood.Talk(it) }
        return Understood.Unknown(transcript)
    }
}
