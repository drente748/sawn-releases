package com.sawn.domain.block

/**
 * Curated adult-content domains shipped offline with the app. Drawn from actively
 * maintained open blocklists (PornAway, StevenBlack unified hosts "adult" extension,
 * 1Hosts "Ultra") and condensed to the sites that carry most real-world traffic.
 *
 * The matcher treats every entry as a suffix match, so "pornhub.com" also covers
 * "www.pornhub.com" and "api.pornhub.com" without extra entries.
 *
 * This list is the offline floor — deliberate minimalism so the APK stays small; the
 * full-history blocklists can extend it at runtime via [AdultListFetcher].
 */
object AdultHosts {

    val CORE: List<String> = listOf(
        // Major tube/video platforms ---------------------------------------------
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
        "youporn.com", "txxx.com", "spankbang.com", "eporner.com", "tube8.com",
        "drtuber.com", "4tube.com", "porntrex.com", "beeg.com", "hqporner.com",
        "ixxx.com", "tnaflix.com", "empflix.com", "porndoe.com", "upornia.com",
        "porn.com", "pornerbros.com", "submityourflicks.com", "manhub.com",
        "nuvid.com", "sunporno.com", "freeporn.com", "thenewporn.com",
        "cliphunter.com", "fetishshrine.com", "hotmovs.com", "hclips.com",
        "anysex.com", "jizzbunker.com", "watchmygf.me", "shemalez.com",
        "thisav.com", "porntn.com", "desixnxx.net", "motherless.com",
        // Gay / trans / shemale surfacing ----------------------------------------
        "boyfriendtv.com", "gaymaletube.com", "thisvid.com", "gay0day.com",
        "yeswegays.com", "thegay.com", "mintmen.com",
        // Live cams -----------------------------------------------------------------
        "chaturbate.com", "stripchat.com", "livejasmin.com", "bongacams.com",
        "cam4.com", "camsoda.com", "myfreecams.com", "flirt4free.com",
        "imlive.com", "cams.com", "streamate.com", "camonster.com",
        // Subscription / amateur ("creator economy") ------------------------------
        "onlyfans.com", "fansly.com", "fanvue.com", "justfor.fans",
        "manyvids.com", "modelhub.com", "passion-hd.net",
        // Hentai / anime / drawn ----------------------------------------------------
        "hanime.tv", "nhentai.net", "hentaihaven.xxx", "rule34.xxx",
        "rule34video.com", "e621.net", "e926.net", "exhentai.org",
        "hentainexus.com", "kemono.party", "coomer.party", "simpcity.su",
        "f95zone.to", "hbox.me", "ahegao.fr",
        // Aggregators / search / NSFW social ---------------------------------------
        "pornmd.com", "pornpen.ai", "fapello.com", "sex.com",
        "thothub.tv", "leakedmodels.com", "bunkr.is", "bunkr.su",
        "motherless.com", "scrolller.com",
        // Arabic / regional -----------------------------------------------------------
        "arabsexweb.com", "arabsex.com", "arabhotsex.com", "sexarab.com",
        "xn-arab.com", "alkonuz.com", "arabseed.adult",
        // Shorts-like adult shorts (TikTok for porn) --------------------------------
        "porntiktok.com", "naughtytiktok.com", "redgifs.com", "licensing.redgifs.com",
    ).map { it.trim().lowercase() }.distinct()

    /**
     * Reddit's NSFW surfaces are handled by domain: reddit serves 18+ media from
     * i.redd.it / reddit-media.com / reddit.com, so we must not blanket-block the whole
     * domain, only the media subdomains. Blocking www.reddit.com would trap spreadsheets
     * and programming help, and break r/NoFap. So we target the media paths.
     */
    val REDDIT_MEDIA: List<String> = listOf(
        "i.redd.it", "v.redd.it", "reddit-media.com",
    ).map { it.trim().lowercase() }.distinct()

    /** Broad extension sets; deliberately larger than CORE. */
    val EXTENDED: List<String> = listOf(
        "fux.com", "porndig.com", "porngo.com", "porn300.com", "porn555.com",
        "pornone.com", "hdsex.org", "tubxporn.com", "beeg.porn", "xhamster3.com",
        "xhamster.desi", "xxxmovies.fun", "pornky.club", "bigtits.buzz",
        "pomapoo.info", "analdin.com", "dietyskin.com", "cambro.tv",
        "camvideos.org", "xxx stripes.com", "x-apk.com", "jav.guru",
        "javhd.today", "javtube.com", "missav.com", "sexykudo.com",
    ).map { it.trim().lowercase().replace(" ", "") }.filter { it.isNotBlank() }.distinct()

    val ALL: Set<String> = (CORE + EXTENDED).toSortedSet()

    fun isAdultDomain(domain: String): Boolean = domain in ALL

    /** عدد النطاقات المدمجة — يُعرض ثابتاً في بطاقة الرئيسية (قرار 2026-11-01). */
    fun includedCount(): Int = ALL.size
}
