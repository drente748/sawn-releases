package com.sawn.domain

/** إجابات الترحيب الأربع + الملف المحلي — بلا أي شبكة، كلها على الجهاز. */
data class OnboardingAnswers(
    val source: String = "",
    val goal: String = "",
    val commitment: String = "",
    val content: Set<String> = emptySet(),
    val name: String? = null,
    val age: Int? = null,
) {
    fun serialize(): String =
        listOf(source, goal, commitment, content.sorted().joinToString(","), name.orEmpty(), age?.toString().orEmpty())
            .joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "|"

        fun deserialize(raw: String?): OnboardingAnswers? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(SEPARATOR)
            if (parts.size < 6) return null
            return OnboardingAnswers(
                source = parts[0],
                goal = parts[1],
                commitment = parts[2],
                content = if (parts[3].isBlank()) emptySet() else parts[3].split(',').filter { it.isNotBlank() }.toSet(),
                name = parts[4].ifBlank { null },
                age = parts[5].toIntOrNull(),
            )
        }
    }
}
