package com.nexus.stream.domain.model

data class OpenSourceNotice(
    val name: String,
    val license: String,
    val url: String,
)

val OPEN_SOURCE_NOTICES: List<OpenSourceNotice> =
    listOf(
        OpenSourceNotice(
            name = "AndroidX Core KTX",
            license = "Apache 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/core",
        ),
        OpenSourceNotice(
            name = "AndroidX Lifecycle",
            license = "Apache 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
        ),
        OpenSourceNotice(
            name = "Coil",
            license = "Apache 2.0",
            url = "https://github.com/coil-kt/coil",
        ),
        OpenSourceNotice(
            name = "DataStore",
            license = "Apache 2.0",
            url = "https://developer.android.com/topic/libraries/architecture/datastore",
        ),
        OpenSourceNotice(
            name = "DM Sans",
            license = "SIL Open Font License 1.1",
            url = "https://github.com/googlefonts/dm-fonts",
        ),
        OpenSourceNotice(
            name = "Figtree",
            license = "SIL Open Font License 1.1",
            url = "https://github.com/erikdkennedy/figtree",
        ),
        OpenSourceNotice(
            name = "Hilt & Dagger",
            license = "Apache 2.0",
            url = "https://github.com/google/dagger",
        ),
        OpenSourceNotice(
            name = "Inter",
            license = "SIL Open Font License 1.1",
            url = "https://github.com/rsms/inter",
        ),
        OpenSourceNotice(
            name = "Jetpack Compose",
            license = "Apache 2.0",
            url = "https://developer.android.com/jetpack/compose",
        ),
        OpenSourceNotice(
            name = "jlibtorrent",
            license = "MIT License",
            url = "https://github.com/frostwire/frostwire-jlibtorrent",
        ),
        OpenSourceNotice(
            name = "kotlinx.coroutines",
            license = "Apache 2.0",
            url = "https://github.com/Kotlin/kotlinx.coroutines",
        ),
        OpenSourceNotice(
            name = "kotlinx.serialization",
            license = "Apache 2.0",
            url = "https://github.com/Kotlin/kotlinx.serialization",
        ),
        OpenSourceNotice(
            name = "Lexend",
            license = "SIL Open Font License 1.1",
            url = "https://github.com/ThomasJockin/lexend",
        ),
        OpenSourceNotice(
            name = "libtorrent",
            license = "BSD-3-Clause",
            url = "https://github.com/arvidn/libtorrent",
        ),
        OpenSourceNotice(
            name = "libVLC",
            license = "LGPL-2.1",
            url = "https://code.videolan.org/videolan/vlc-android",
        ),
        OpenSourceNotice(
            name = "Manrope",
            license = "SIL Open Font License 1.1",
            url = "https://github.com/gentunian/manrope",
        ),
        OpenSourceNotice(
            name = "Material 3",
            license = "Apache 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/compose-material3",
        ),
        OpenSourceNotice(
            name = "MaterialKolor",
            license = "MIT License",
            url = "https://github.com/jordond/MaterialKolor",
        ),
        OpenSourceNotice(
            name = "Navigation Compose",
            license = "Apache 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/navigation",
        ),
        OpenSourceNotice(
            name = "Nunito",
            license = "SIL Open Font License 1.1",
            url = "https://github.com/googlefonts/nunito",
        ),
        OpenSourceNotice(
            name = "OkHttp",
            license = "Apache 2.0",
            url = "https://github.com/square/okhttp",
        ),
        OpenSourceNotice(
            name = "Outfit",
            license = "SIL Open Font License 1.1",
            url = "https://github.com/Outfitio/Outfit-Fonts",
        ),
        OpenSourceNotice(
            name = "Plus Jakarta Sans",
            license = "SIL Open Font License 1.1",
            url = "https://github.com/tokotype/PlusJakartaSans",
        ),
        OpenSourceNotice(
            name = "Poppins",
            license = "SIL Open Font License 1.1",
            url = "https://github.com/itfoundry/Poppins",
        ),
        OpenSourceNotice(
            name = "Retrofit",
            license = "Apache 2.0",
            url = "https://github.com/square/retrofit",
        ),
        OpenSourceNotice(
            name = "Room",
            license = "Apache 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/room",
        ),
        OpenSourceNotice(
            name = "Space Grotesk",
            license = "SIL Open Font License 1.1",
            url = "https://github.com/floriankarsten/space-grotesk",
        ),
    )
