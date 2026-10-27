package com.example.tubelight

import android.net.Uri

/**
 * Ad blocking has three layers:
 *  1. Network: requests to known ad hosts and ad paths are answered with an empty response.
 *  2. Data: ad entries are removed from the player data the page receives, so no ad is scheduled.
 *  3. Page: ad elements are hidden, and any ad that still starts is skipped or fast-forwarded.
 *
 * YouTube changes its page often. When ads come back, the selectors and keys below are what to update.
 */
object AdBlocker {

    private val adHosts = listOf(
        "googleads.g.doubleclick.net",
        "static.doubleclick.net",
        "ad.doubleclick.net",
        "pagead2.googlesyndication.com",
        "ade.googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "imasdk.googleapis.com"
    )

    private val adPathParts = listOf(
        "/pagead/",
        "/api/stats/ads",
        "/ptracking",
        "/get_midroll_"
    )

    private val appHostSuffixes = listOf(
        "youtube.com", "youtu.be", "ytimg.com", "googlevideo.com",
        "gstatic.com", "googleusercontent.com", "ggpht.com"
    )

    private val googleHost = Regex("(^|\\.)google\\.[a-z]{2,3}(\\.[a-z]{2})?$")

    fun isAdRequest(uri: Uri): Boolean {
        val host = uri.host ?: return false
        if (adHosts.any { host == it || host.endsWith(".$it") }) return true
        if (host.endsWith("youtube.com")) {
            val path = uri.path ?: return false
            if (adPathParts.any { path.contains(it) }) return true
        }
        return false
    }

    /** Pages on these hosts stay inside the app. Everything else opens in the browser. */
    fun isAppHost(host: String): Boolean =
        appHostSuffixes.any { host == it || host.endsWith(".$it") } || googleHost.containsMatchIn(host)

    /** Runs inside the page before YouTube's own scripts. Reads settings from window.__vtPrefs. */
    val SCRIPT = """
(function () {
  if (window.__vt) return;
  var P = window.__vtPrefs || {};

  var AD_CSS =
    'ytm-promoted-sparkles-web-renderer,ytm-promoted-video-renderer,ytm-companion-ad-renderer,' +
    'ytm-display-ad-renderer,ytm-brand-video-singleton-renderer,ytm-in-feed-ad-layout-renderer,' +
    'ytm-ad-slot-renderer,ad-slot-renderer,ytm-statement-banner-renderer,#player-ads,' +
    '.ytp-ad-overlay-container,.ytp-ad-module,.video-ads{display:none!important}';
  var SHORTS_CSS =
    'ytm-reel-shelf-renderer,ytm-shorts-lockup-view-model,ytm-shorts-lockup-view-model-v2,' +
    'ytm-rich-section-renderer:has(a[href^="/shorts"]),' +
    'ytm-pivot-bar-item-renderer:has(a[href^="/shorts"]){display:none!important}';
  var COMMENTS_CSS =
    'ytm-comments-entry-point-header-renderer,ytm-comment-section-renderer,' +
    'ytm-item-section-renderer[section-identifier="comment-item-section"],' +
    'ytm-item-section-renderer[section-identifier="comments-entry-point"]{display:none!important}';

  var style = document.createElement('style');
  function applyCss() {
    var s = '';
    if (P.blockAds) s += AD_CSS;
    if (P.hideShorts) s += SHORTS_CSS;
    if (P.hideComments) s += COMMENTS_CSS;
    style.textContent = s;
  }
  applyCss();
  (document.head || document.documentElement).appendChild(style);

  /* Layer 2: remove ad entries from player data. */
  function strip(o) {
    if (!P.blockAds || !o || typeof o !== 'object') return;
    if (o.adPlacements) o.adPlacements = [];
    if (o.playerAds) o.playerAds = [];
    if (o.adSlots) o.adSlots = [];
  }
  var origParse = JSON.parse;
  JSON.parse = function () {
    var r = origParse.apply(this, arguments);
    try { strip(r); } catch (e) {}
    return r;
  };
  if (window.Response && Response.prototype.json) {
    var origJson = Response.prototype.json;
    Response.prototype.json = function () {
      return origJson.call(this).then(function (r) { try { strip(r); } catch (e) {} return r; });
    };
  }

  /* Keep playing when the app is in the background. */
  try {
    var hid = Object.getOwnPropertyDescriptor(Document.prototype, 'hidden');
    var vis = Object.getOwnPropertyDescriptor(Document.prototype, 'visibilityState');
    Object.defineProperty(document, 'hidden', {
      configurable: true,
      get: function () { return P.backgroundPlay ? false : hid.get.call(document); }
    });
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: function () { return P.backgroundPlay ? 'visible' : vis.get.call(document); }
    });
    document.addEventListener('visibilitychange', function (e) {
      if (P.backgroundPlay) e.stopImmediatePropagation();
    }, true);
  } catch (e) {}

  /* Sleep timer option "End of video": hold playback once the video ends. */
  document.addEventListener('ended', function () {
    if (!P.sleepAtEnd) return;
    P.sleepAtEnd = false;
    var n = 0;
    var t = setInterval(function () {
      var v = document.querySelector('video');
      if (v) v.pause();
      if (++n > 20) clearInterval(t);
    }, 200);
  }, true);

  /* Layer 3: skip ads that still start, apply speed and loop. */
  var mutedByUs = false;
  setInterval(function () {
    var v = document.querySelector('video');
    if (!v) return;
    var player = document.querySelector('.html5-video-player');
    var adShowing = !!(player && player.classList.contains('ad-showing'));
    if (P.blockAds && adShowing) {
      var skip = document.querySelector(
        '.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-ad-skip-button-container button');
      if (skip) skip.click();
      if (!v.muted) { v.muted = true; mutedByUs = true; }
      if (isFinite(v.duration) && v.duration > 0 && v.currentTime < v.duration - 0.2) {
        v.currentTime = v.duration - 0.1;
      }
      return;
    }
    if (mutedByUs) { v.muted = false; mutedByUs = false; }
    var sp = P.speed || 1;
    if (v.playbackRate !== sp) v.playbackRate = sp;
    v.loop = !!P.loop;
  }, 400);

  /* Floating player: stretch the video over the whole window. */
  var pipStyle = document.createElement('style');
  pipStyle.textContent =
    'video{position:fixed!important;top:0!important;left:0!important;width:100vw!important;' +
    'height:100vh!important;z-index:2147483647!important;object-fit:contain!important;background:#000!important}';

  window.__vt = {
    set: function (p) { P = p; applyCss(); },
    pip: function (on) {
      if (on) document.documentElement.appendChild(pipStyle);
      else if (pipStyle.parentNode) pipStyle.parentNode.removeChild(pipStyle);
    }
  };
})();
"""
}
