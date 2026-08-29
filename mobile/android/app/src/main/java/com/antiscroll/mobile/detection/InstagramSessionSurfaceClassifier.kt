package com.antiscroll.mobile.detection

import java.text.Normalizer
import java.util.Locale

enum class InstagramSessionSurface {
    COUNTED,
    PAUSED_MESSAGES,
    PAUSED_PROFILE,
}

class InstagramSessionSurfaceClassifier {
    fun classify(
        eventClassName: String?,
        root: UiNodeSnapshot?,
    ): InstagramSessionSurface {
        classifyEventClass(eventClassName)?.let { return it }
        if (root == null) return InstagramSessionSurface.COUNTED

        var profileSelected = false
        var messagesSelected = false
        var profileResourceFound = false
        var messagesResourceFound = false
        var countedTabSelected = false
        var countedResourceFound = false
        val visibleLabels = mutableSetOf<String>()

        root.walkVisible().forEach { node ->
            val resourceId = node.resourceId.normalized()
            val labels = listOf(node.text, node.contentDescription)
                .map { it.normalized() }
            visibleLabels += labels.filter(String::isNotEmpty)

            if (PROFILE_RESOURCE_MARKERS.any(resourceId::contains)) {
                profileResourceFound = true
            }
            if (MESSAGE_RESOURCE_MARKERS.any(resourceId::contains)) {
                messagesResourceFound = true
            }
            if (COUNTED_RESOURCE_MARKERS.any(resourceId::contains)) {
                countedResourceFound = true
            }
            if (node.selected && labels.any(PROFILE_SELECTED_LABELS::contains)) {
                profileSelected = true
            }
            if (node.selected && labels.any(MESSAGE_SELECTED_LABELS::contains)) {
                messagesSelected = true
            }
            if (node.selected && labels.any(COUNTED_SELECTED_LABELS::contains)) {
                countedTabSelected = true
            }
        }

        val messageScreenScore = MESSAGE_SCREEN_LABEL_GROUPS.count { group ->
            group.any { marker ->
                visibleLabels.any { label -> label.matchesScreenLabel(marker) }
            }
        }
        val profileScreenScore = PROFILE_SCREEN_LABEL_GROUPS.count { group ->
            group.any { marker ->
                visibleLabels.any { label -> label.contains(marker) }
            }
        }

        return when {
            messagesResourceFound || messagesSelected ->
                InstagramSessionSurface.PAUSED_MESSAGES
            profileResourceFound || profileSelected ->
                InstagramSessionSurface.PAUSED_PROFILE
            countedResourceFound || countedTabSelected ->
                InstagramSessionSurface.COUNTED
            messageScreenScore >= 2 ->
                InstagramSessionSurface.PAUSED_MESSAGES
            profileScreenScore >= 3 ->
                InstagramSessionSurface.PAUSED_PROFILE
            else ->
                InstagramSessionSurface.COUNTED
        }
    }

    fun classifyEventClass(eventClassName: String?): InstagramSessionSurface? {
        val className = eventClassName.normalized()
        return when {
            MESSAGE_CLASS_MARKERS.any(className::contains) ->
                InstagramSessionSurface.PAUSED_MESSAGES
            PROFILE_CLASS_MARKERS.any(className::contains) ->
                InstagramSessionSurface.PAUSED_PROFILE
            else ->
                null
        }
    }

    private fun String?.normalized(): String =
        Normalizer.normalize(this.orEmpty(), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
            .replace("…", "...")
            .trim()
            .lowercase(Locale.ROOT)

    private fun String.matchesScreenLabel(marker: String): Boolean =
        this == marker ||
            startsWith("$marker,") ||
            startsWith("$marker (")

    private companion object {
        val DIACRITICS_REGEX = "\\p{M}+".toRegex()

        val MESSAGE_CLASS_MARKERS = listOf(
            ".direct.",
            "directinbox",
            "directthread",
            "directmessaging",
            "messagingactivity",
            "inboxactivity",
        )

        val PROFILE_CLASS_MARKERS = listOf(
            "profilefragment",
            "profileactivity",
        )

        val MESSAGE_RESOURCE_MARKERS = listOf(
            ":id/direct_inbox",
            ":id/direct_thread",
            ":id/direct_messaging",
            ":id/inbox_recycler",
            ":id/direct_thread_recycler",
        )

        val PROFILE_RESOURCE_MARKERS = listOf(
            ":id/profile_header",
            ":id/profile_screen",
            ":id/profile_grid",
            ":id/profile_recycler",
        )

        val COUNTED_RESOURCE_MARKERS = listOf(
            ":id/feed_recycler",
            ":id/feed_list",
            ":id/main_feed",
            ":id/explore_grid",
            ":id/explore_recycler",
        )

        val MESSAGE_SELECTED_LABELS = setOf(
            "messages",
            "messages tab",
            "messenger",
            "direct",
            "chats",
            "discussions",
            "boite de reception",
        )

        val PROFILE_SELECTED_LABELS = setOf(
            "profile",
            "profile tab",
            "your profile",
            "profil",
            "onglet profil",
            "votre profil",
        )

        val COUNTED_SELECTED_LABELS = setOf(
            "home",
            "home tab",
            "feed",
            "accueil",
            "fil",
            "explore",
            "explore tab",
            "search",
            "rechercher",
            "decouvrir",
        )

        // Instagram sometimes exposes generic activity names and obfuscated
        // resource IDs. Requiring several distinct label groups recognizes
        // those screens without pausing on Messages/Profile-related buttons
        // that are also present in the normal feed.
        val MESSAGE_SCREEN_LABEL_GROUPS = listOf(
            setOf(
                "messages",
                "your messages",
                "vos messages",
                "discussions",
                "boite de reception",
            ),
            setOf(
                "requests",
                "message requests",
                "invitations",
                "demandes",
            ),
            setOf(
                "new message",
                "compose",
                "nouveau message",
                "ecrire un message",
            ),
            setOf(
                "message...",
                "write a message...",
                "ecrire un message...",
            ),
            setOf("send", "envoyer"),
            setOf(
                "audio call",
                "video call",
                "appel audio",
                "appel video",
            ),
        )

        val PROFILE_SCREEN_LABEL_GROUPS = listOf(
            setOf("posts", "publications"),
            setOf("followers", "abonnes"),
            setOf("following", "abonnements"),
            setOf("edit profile", "modifier le profil"),
            setOf("share profile", "partager le profil"),
        )
    }
}
