package com.salahlock.app.verification

object ReminderRepository {

    private val quranReminders = listOf(
        Reminder(
            id = 1, type = ReminderType.QURAN,
            arabic = "يَا أَيُّهَا الَّذِينَ آمَنُوا اتَّقُوا اللَّهَ وَكُونُوا مَعَ الصَّادِقِينَ",
            translation = "O you who believe, fear Allah and be with those who are truthful.",
            reference = "Quran 9:119",
        ),
        Reminder(
            id = 2, type = ReminderType.QURAN,
            arabic = "أَقِمِ الصَّلَاةَ لِذِكْرِي",
            translation = "Establish prayer for My remembrance.",
            reference = "Quran 20:14",
        ),
        Reminder(
            id = 3, type = ReminderType.QURAN,
            arabic = "إِنَّ الصَّلَاةَ تَنْهَىٰ عَنِ الْفَحْشَاءِ وَالْمُنكَرِ",
            translation = "Indeed, prayer restrains from immorality and wrongdoing.",
            reference = "Quran 29:45",
        ),
        Reminder(
            id = 4, type = ReminderType.QURAN,
            arabic = "وَاسْتَعِينُوا بِالصَّبْرِ وَالصَّلَاةِ",
            translation = "Seek help through patience and prayer.",
            reference = "Quran 2:45",
        ),
        Reminder(
            id = 5, type = ReminderType.QURAN,
            arabic = "قَدْ أَفْلَحَ الْمُؤْمِنُونَ ۝ الَّذِينَ هُمْ فِي صَلَاتِهِمْ خَاشِعُونَ",
            translation = "Successful indeed are the believers — those who are humble in their prayers.",
            reference = "Quran 23:1–2",
        ),
        Reminder(
            id = 6, type = ReminderType.QURAN,
            arabic = "حَافِظُوا عَلَى الصَّلَوَاتِ وَالصَّلَاةِ الْوُسْطَىٰ وَقُومُوا لِلَّهِ قَانِتِينَ",
            translation = "Maintain all prayers, and the middle prayer, and stand before Allah with reverence.",
            reference = "Quran 2:238",
        ),
    )

    private val hadithReminders = listOf(
        Reminder(
            id = 10, type = ReminderType.HADITH,
            arabic = null,
            translation = "Truthfulness leads to righteousness, and righteousness leads to Paradise.",
            reference = "Sahih al-Bukhari",
        ),
        Reminder(
            id = 11, type = ReminderType.HADITH,
            arabic = null,
            translation = "The first matter that a servant will be questioned about on the Day of Judgement is prayer.",
            reference = "Sunan Abi Dawud",
        ),
        Reminder(
            id = 12, type = ReminderType.HADITH,
            arabic = null,
            translation = "Prayer is the pillar of religion. Whoever establishes it has established religion, and whoever destroys it has destroyed religion.",
            reference = "Shu'ab al-Iman",
        ),
        Reminder(
            id = 13, type = ReminderType.HADITH,
            arabic = null,
            translation = "The coolness of my eyes has been placed in prayer.",
            reference = "Sunan an-Nasa'i",
        ),
        Reminder(
            id = 14, type = ReminderType.HADITH,
            arabic = null,
            translation = "If there was a river at the door of anyone of you and he took a bath in it five times a day, would you notice any dirt on him? They said: Not a trace of dirt would be left. The Prophet said: That is the parable of the five prayers by which Allah wipes out sins.",
            reference = "Sahih al-Bukhari",
        ),
        Reminder(
            id = 15, type = ReminderType.HADITH,
            arabic = null,
            translation = "Make things easy and do not make them difficult. Give good tidings and do not drive people away.",
            reference = "Sahih al-Bukhari",
        ),
    )

    private val reflectionReminders = listOf(
        Reminder(
            id = 20, type = ReminderType.REFLECTION,
            arabic = null,
            translation = "Only Allah knows what is in our hearts. This reminder is for reflection and sincerity.",
            reference = "Reflection",
        ),
        Reminder(
            id = 21, type = ReminderType.REFLECTION,
            arabic = null,
            translation = "Prayer is a conversation with Allah. Each salah is a gift, not a burden.",
            reference = "Reflection",
        ),
        Reminder(
            id = 22, type = ReminderType.REFLECTION,
            arabic = null,
            translation = "The sincerity of our intentions is between us and Allah alone. May He accept what we offer.",
            reference = "Reflection",
        ),
        Reminder(
            id = 23, type = ReminderType.REFLECTION,
            arabic = null,
            translation = "May Allah make our prayers a source of peace and closeness to Him.",
            reference = "Reflection",
        ),
        Reminder(
            id = 24, type = ReminderType.REFLECTION,
            arabic = null,
            translation = "Every prayer you complete is a step toward the person you are striving to become.",
            reference = "Reflection",
        ),
    )

    fun getRandom(
        includeQuran: Boolean = true,
        includeHadith: Boolean = true,
        includeReflection: Boolean = true,
    ): Reminder {
        val pool = buildList {
            if (includeQuran) addAll(quranReminders)
            if (includeHadith) addAll(hadithReminders)
            if (includeReflection) addAll(reflectionReminders)
        }
        return if (pool.isEmpty()) reflectionReminders.random() else pool.random()
    }
}
