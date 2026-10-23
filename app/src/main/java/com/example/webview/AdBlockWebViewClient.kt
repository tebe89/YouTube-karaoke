package com.example.webview

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * High-Performance NewPipe-Grade AdBlock & Real-Time Audio DSP WebViewClient
 * for karaokeTB.
 *
 * Features:
 * 1. NewPipe-grade Ad Blocking:
 *    - Request interception blocking all ad servers and tracking domains
 *    - JSON payload interception stripping adPlacements and playerAds from YouTube player API
 *    - Global CSS injection hiding all banners, promo overlays, and ad containers
 *    - Safe fast-forwarding of video ads without cutting main songs
 * 2. Stutter-Free Zero-Latency Audio DSP:
 *    - Native Web Audio graph bypasses processing when pitch is 0.0 ST for pure, stutter-free hardware audio
 *    - Direct hardware-accelerated L/R Channel Matrix Router (Stereo, Minus-One Music L, Vocal Guide R, Mono)
 *    - Dual-Delay Hann-Window Pitch Shifting strictly locked at 1.0x playback speed (zero tempo change)
 * 3. Anti-Pause Engine:
 *    - Prevents YouTube mobile from pausing when minimized or rendered in normal deck container
 *    - Overrides document visibility and intersection monitor so video never auto-pauses
 */
open class AdBlockWebViewClient(
    private val onPageTitleChanged: ((String) -> Unit)? = null,
    private val onAdBlockedCountChanged: ((Int) -> Unit)? = null
) : WebViewClient() {

    companion object {
        private const val TAG = "karaokeTB_AdBlock"

        // Comprehensive ad network and tracking hosts (NewPipe & uBlock list)
        private val AD_HOST_PATTERNS = listOf(
            "googleads.g.doubleclick.net",
            "pubads.g.doubleclick.net",
            "securepubads.g.doubleclick.net",
            "pagead2.googlesyndication.com",
            "adservice.google.com",
            "static.doubleclick.net",
            "fls-na.amazon.com",
            "/pagead/",
            "/api/stats/ads",
            "/api/stats/atr",
            "/ptracking",
            "/generate_204?adformat",
            "doubleclick.net",
            "googlesyndication.com",
            "youtube.com/api/stats/playback",
            "youtubei/v1/player/ad_break",
            "/get_midroll_info",
            "ad_type=",
            "adformat="
        )

        val INJECTED_DSP_AND_ADBLOCK_JS = """
            (function() {
                // =====================================================================
                // 1. NEWPIPE-GRADE SCRIPT & RESPONSE INTERCEPTION (STRIP ADS AT SOURCE)
                // =====================================================================
                try {
                    // Strip adPlacements from initial player response if present
                    if (window.ytInitialPlayerResponse && window.ytInitialPlayerResponse.adPlacements) {
                        delete window.ytInitialPlayerResponse.adPlacements;
                        delete window.ytInitialPlayerResponse.playerAds;
                    }
                    Object.defineProperty(window, 'ytInitialPlayerResponse', {
                        configurable: true,
                        set: function(val) {
                            if (val && typeof val === 'object') {
                                delete val.adPlacements;
                                delete val.playerAds;
                                delete val.adSlots;
                            }
                            this._ytInitialPlayerResponse = val;
                        },
                        get: function() {
                            return this._ytInitialPlayerResponse;
                        }
                    });

                    // Intercept fetch API to strip adPlacements from YouTube internal responses
                    const origFetch = window.fetch;
                    window.fetch = async function(...args) {
                        const response = await origFetch.apply(this, args);
                        try {
                            const url = (args[0] && args[0].url) || args[0] || '';
                            if (typeof url === 'string' && url.includes('/youtubei/v1/player')) {
                                const clone = response.clone();
                                const json = await clone.json();
                                if (json && (json.adPlacements || json.playerAds || json.adSlots)) {
                                    delete json.adPlacements;
                                    delete json.playerAds;
                                    delete json.adSlots;
                                    return new Response(JSON.stringify(json), {
                                        status: response.status,
                                        statusText: response.statusText,
                                        headers: response.headers
                                    });
                                }
                            }
                        } catch (e) {}
                        return response;
                    };
                } catch(e) {
                    console.warn("NewPipe interception note:", e);
                }

                // =====================================================================
                // 2. PERMANENT CSS INJECTION TO HIDE ALL AD & PROMO OVERLAYS
                // =====================================================================
                function injectAdBlockCss() {
                    if (document.getElementById('ktb-adblock-css')) return;
                    const style = document.createElement('style');
                    style.id = 'ktb-adblock-css';
                    style.textContent = `
                        .ad-showing, .ad-interrupting, .video-ads, .ytp-ad-overlay-container,
                        .ytp-ad-module, .ytp-ad-player-overlay, #player-ads,
                        ytm-promoted-sparkles-web-renderer, ytd-banner-promo-renderer,
                        ytm-companion-ad-renderer, ytm-promoted-video-renderer,
                        .companion-ad-container, #masthead-ad, .ad-container,
                        ytm-brand-header-renderer, ytm-statement-banner-renderer,
                        .ytp-ad-skip-button, .ytp-ad-preview-container,
                        ytm-paid-content-overlay-renderer {
                            display: none !important;
                            visibility: hidden !important;
                            opacity: 0 !important;
                            height: 0 !important;
                            width: 0 !important;
                            pointer-events: none !important;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(style);
                }
                injectAdBlockCss();
                document.addEventListener('DOMContentLoaded', injectAdBlockCss);

                // =====================================================================
                // 3. ANTI-PAUSE & BACKGROUND PERSISTENCE ENGINE
                // =====================================================================
                try {
                    Object.defineProperty(document, 'hidden', { get: () => false });
                    Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
                    Object.defineProperty(document, 'webkitHidden', { get: () => false });
                    Object.defineProperty(document, 'webkitVisibilityState', { get: () => 'visible' });
                    document.hasFocus = function() { return true; };

                    ['visibilitychange', 'webkitvisibilitychange', 'blur', 'pagehide'].forEach(evt => {
                        window.addEventListener(evt, (e) => { e.stopImmediatePropagation(); }, true);
                        document.addEventListener(evt, (e) => { e.stopImmediatePropagation(); }, true);
                    });

                    // Track explicit user pause to distinguish from background pauses
                    window.__tbUserPaused = false;
                    ['click', 'touchend', 'keydown'].forEach(evt => {
                        document.addEventListener(evt, (e) => {
                            const btn = e.target && e.target.closest && (
                                e.target.closest('.ytp-play-button') ||
                                e.target.closest('button[aria-label*="Pause" i]') ||
                                e.target.closest('button[aria-label*="Play" i]') ||
                                e.target.closest('.play-button')
                            );
                            if (btn) {
                                const aria = (btn.getAttribute('aria-label') || '').toLowerCase();
                                if (aria.includes('pause')) {
                                    window.__tbUserPaused = true;
                                } else if (aria.includes('play')) {
                                    window.__tbUserPaused = false;
                                }
                            }
                        }, true);
                    });
                } catch(e) {}

                // =====================================================================
                // 4. SMART VIDEO AD ACCELERATOR (SAFE SKIP WITHOUT PAUSING MAIN SONG)
                // =====================================================================
                function skipActiveAd() {
                    const moviePlayer = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
                    const isAdShowing = moviePlayer && (moviePlayer.classList.contains('ad-showing') || moviePlayer.classList.contains('ad-interrupting'));
                    
                    if (isAdShowing) {
                        const video = document.querySelector('video');
                        if (video) {
                            video.muted = true;
                            // Fast forward ad safely
                            video.playbackRate = 16.0;
                        }

                        const skipSelectors = [
                            '.ytp-ad-skip-button',
                            '.ytp-ad-skip-button-modern',
                            '.videoAdUiSkipButton',
                            '.ytp-skip-ad-button',
                            'button.ytp-ad-skip-button-text'
                        ];
                        for (let s of skipSelectors) {
                            const btn = document.querySelector(s);
                            if (btn) {
                                btn.click();
                                break;
                            }
                        }
                    }
                }

                // =====================================================================
                // 5. STUTTER-FREE NATIVE WEB AUDIO DSP (ZERO-SPEED-CHANGE PITCH, L/R & VOCAL REMOVER)
                // =====================================================================
                window.__tbCurrentPitchSemitones = window.__tbCurrentPitchSemitones || 0.0;
                window.__tbCurrentRouting = window.__tbCurrentRouting || 'STEREO';
                window.__tbCurrentVocalMode = window.__tbCurrentVocalMode || 'OFF';
                window.__tbCurrentVocalAttenuation = window.__tbCurrentVocalAttenuation || 0.85;

                window.tbSetPitch = function(semitones) {
                    window.__tbCurrentPitchSemitones = parseFloat(semitones) || 0.0;
                    const video = document.querySelector('video');
                    if (video) {
                        video.playbackRate = 1.0;
                        video.defaultPlaybackRate = 1.0;
                        video.preservesPitch = true;
                    }
                    updateDspRouting();
                };

                window.tbSetRouting = function(mode) {
                    window.__tbCurrentRouting = mode;
                    updateDspRouting();
                };

                window.tbSetVocalRemover = function(mode, attenuation) {
                    window.__tbCurrentVocalMode = mode;
                    if (typeof attenuation !== 'undefined') {
                        window.__tbCurrentVocalAttenuation = parseFloat(attenuation) || 0.85;
                    }
                    updateDspRouting();
                };

                function getAudioContext() {
                    if (!window.__tbAudioCtx) {
                        const AudioCtx = window.AudioContext || window.webkitAudioContext;
                        if (AudioCtx) {
                            // latencyHint: 'playback' gives a generous hardware buffer, eliminating stutter
                            window.__tbAudioCtx = new AudioCtx({ latencyHint: 'playback' });
                        }
                    }
                    if (window.__tbAudioCtx && window.__tbAudioCtx.state === 'suspended') {
                        window.__tbAudioCtx.resume().catch(() => {});
                    }
                    return window.__tbAudioCtx;
                }

                ['click', 'touchstart', 'touchend', 'keydown'].forEach(evt => {
                    document.addEventListener(evt, () => {
                        getAudioContext();
                    }, { passive: true });
                });

                function updateDspRouting() {
                    const ctx = getAudioContext();
                    const video = document.querySelector('video');
                    if (!video || !ctx) return;

                    // Ensure video playback tempo is strictly 1.0x normal
                    if (video.playbackRate !== 1.0 && !video.__tbIsAd) {
                        video.playbackRate = 1.0;
                    }

                    // Setup Web Audio graph once per video element
                    if (!video.__tbConnected) {
                        try {
                            // Do NOT set crossOrigin = 'anonymous' on YouTube blob/MSE streams
                            const source = ctx.createMediaElementSource(video);
                            video.__tbSourceNode = source;

                            // Native C++ ChannelSplitter & ChannelMerger for zero-CPU, stutter-free routing
                            const splitter = ctx.createChannelSplitter(2);
                            const merger = ctx.createChannelMerger(2);

                            // L/R Matrix Gain Nodes
                            const gainLL = ctx.createGain(); // Left -> Left
                            const gainLR = ctx.createGain(); // Left -> Right
                            const gainRL = ctx.createGain(); // Right -> Left
                            const gainRR = ctx.createGain(); // Right -> Right

                            // Sub-Bass Protection High-Pass Filters (~120Hz, Q=0.7071)
                            // Passes only mid/high frequencies (>120Hz) to cancellation paths
                            // so kick drum and sub-bass remain 100% thick and intact!
                            const hpfL = ctx.createBiquadFilter();
                            hpfL.type = 'highpass';
                            hpfL.frequency.value = 120.0;
                            hpfL.Q.value = 0.7071;

                            const hpfR = ctx.createBiquadFilter();
                            hpfR.type = 'highpass';
                            hpfR.frequency.value = 120.0;
                            hpfR.Q.value = 0.7071;

                            // Cross-cancellation gains for Phase Inversion Vocal Remover
                            // cancelRintoL feeds inverted R_high into L
                            // cancelLintoR feeds inverted L_high into R
                            const cancelRintoL = ctx.createGain();
                            const cancelLintoR = ctx.createGain();
                            cancelRintoL.gain.value = 0.0;
                            cancelLintoR.gain.value = 0.0;

                            // Direct bypass gain (when pitch is 0.0 ST for 100% stutter-free hardware audio)
                            const directGain = ctx.createGain();
                            const pitchGain = ctx.createGain();
                            directGain.gain.value = 1.0;
                            pitchGain.gain.value = 0.0;

                            // Dual-delay pitch shifter node (preserves 1.0x speed)
                            const bufferSize = 2048;
                            const pitchNode = ctx.createScriptProcessor(bufferSize, 2, 2);
                            const MAX_DELAY = 8192;
                            const delayL = new Float32Array(MAX_DELAY);
                            const delayR = new Float32Array(MAX_DELAY);
                            let writeIdx = 0;
                            let phase = 0.0;
                            const windowSize = 2048;

                            pitchNode.onaudioprocess = function(e) {
                                const inL = e.inputBuffer.getChannelData(0);
                                const inR = e.inputBuffer.getChannelData(1);
                                const outL = e.outputBuffer.getChannelData(0);
                                const outR = e.outputBuffer.getChannelData(1);

                                const semitones = window.__tbCurrentPitchSemitones || 0.0;
                                if (Math.abs(semitones) < 0.01) {
                                    outL.set(inL);
                                    outR.set(inR);
                                    return;
                                }

                                const pitchRatio = Math.pow(2.0, semitones / 12.0);
                                const rate = (1.0 - pitchRatio) / windowSize;

                                for (let i = 0; i < inL.length; i++) {
                                    delayL[writeIdx] = inL[i];
                                    delayR[writeIdx] = inR[i];

                                    phase += rate;
                                    if (phase < 0.0) phase += 1.0;
                                    if (phase >= 1.0) phase -= 1.0;

                                    const phase0 = phase;
                                    const phase1 = (phase0 < 0.5) ? (phase0 + 0.5) : (phase0 - 0.5);

                                    // Hann window crossfading (constant power)
                                    const gain0 = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * phase0));
                                    const gain1 = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * phase1));

                                    let r0 = Math.floor(writeIdx - (phase0 * windowSize));
                                    r0 = (r0 % MAX_DELAY + MAX_DELAY) % MAX_DELAY;
                                    let r1 = Math.floor(writeIdx - (phase1 * windowSize));
                                    r1 = (r1 % MAX_DELAY + MAX_DELAY) % MAX_DELAY;

                                    outL[i] = delayL[r0] * gain0 + delayL[r1] * gain1;
                                    outR[i] = delayR[r0] * gain0 + delayR[r1] * gain1;

                                    writeIdx = (writeIdx + 1) % MAX_DELAY;
                                }
                            };

                            // Audio Graph Wiring:
                            // Source -> directGain -> splitter
                            // Source -> pitchNode -> pitchGain -> splitter
                            source.connect(directGain);
                            source.connect(pitchNode);
                            pitchNode.connect(pitchGain);

                            directGain.connect(splitter);
                            pitchGain.connect(splitter);

                            // L/R Matrix Routing
                            splitter.connect(gainLL, 0);
                            splitter.connect(gainLR, 0);
                            splitter.connect(gainRL, 1);
                            splitter.connect(gainRR, 1);

                            // Connect splitter to High-Pass Filters for Sub-Bass Protected Vocal Remover
                            splitter.connect(hpfL, 0);
                            splitter.connect(hpfR, 1);
                            hpfR.connect(cancelRintoL);
                            hpfL.connect(cancelLintoR);

                            // Left Channel Merger (Input 0): L_direct + inverted R_high
                            gainLL.connect(merger, 0, 0);
                            gainRL.connect(merger, 0, 0);
                            cancelRintoL.connect(merger, 0, 0);

                            // Right Channel Merger (Input 1): R_direct + inverted L_high
                            gainLR.connect(merger, 0, 1);
                            gainRR.connect(merger, 0, 1);
                            cancelLintoR.connect(merger, 0, 1);

                            merger.connect(ctx.destination);

                            video.__tbDirectGain = directGain;
                            video.__tbPitchGain = pitchGain;
                            video.__tbGainLL = gainLL;
                            video.__tbGainLR = gainLR;
                            video.__tbGainRL = gainRL;
                            video.__tbGainRR = gainRR;
                            video.__tbCancelRintoL = cancelRintoL;
                            video.__tbCancelLintoR = cancelLintoR;
                            video.__tbConnected = true;
                        } catch (err) {
                            console.log("WebAudio setup note:", err.message);
                        }
                    }

                    // Dynamically toggle direct hardware bypass vs pitch shifter
                    const semitones = window.__tbCurrentPitchSemitones || 0.0;
                    if (video.__tbDirectGain && video.__tbPitchGain) {
                        if (Math.abs(semitones) < 0.01) {
                            video.__tbDirectGain.gain.value = 1.0;
                            video.__tbPitchGain.gain.value = 0.0;
                        } else {
                            video.__tbDirectGain.gain.value = 0.0;
                            video.__tbPitchGain.gain.value = 1.0;
                        }
                    }

                    // Apply Vocal Remover Mode (Phase Inversion + 120Hz Sub-Bass Protection)
                    // Full -1.0x inversion cancels center-panned vocals completely (L - R = 0)
                    const vocalMode = window.__tbCurrentVocalMode || 'OFF';
                    const vocalAtten = (typeof window.__tbCurrentVocalAttenuation !== 'undefined') ? window.__tbCurrentVocalAttenuation : 0.85;
                    if (video.__tbCancelRintoL && video.__tbCancelLintoR) {
                        if (vocalMode === 'VOCAL_CUT') {
                            // Phase inversion 180°: -1.0 * attenuation * R_high added to L_in
                            // Center vocals (L == R) completely cancel out: L - 1.0*R = 0
                            video.__tbCancelRintoL.gain.value = -1.0 * vocalAtten;
                            video.__tbCancelLintoR.gain.value = -1.0 * vocalAtten;
                        } else if (vocalMode === 'VOCAL_ISOLATION') {
                            // Center Vocal Isolation
                            video.__tbCancelRintoL.gain.value = 0.5 * vocalAtten;
                            video.__tbCancelLintoR.gain.value = 0.5 * vocalAtten;
                        } else {
                            // Normal stereo passthrough
                            video.__tbCancelRintoL.gain.value = 0.0;
                            video.__tbCancelLintoR.gain.value = 0.0;
                        }
                    }

                    // Apply L/R Matrix Routing
                    const mode = window.__tbCurrentRouting;
                    if (video.__tbGainLL && video.__tbGainLR && video.__tbGainRL && video.__tbGainRR) {
                        if (mode === 'LEFT_ONLY') {
                            // Minus-One Instrumental (Left Channel to both ears)
                            video.__tbGainLL.gain.value = 1.0;
                            video.__tbGainLR.gain.value = 1.0;
                            video.__tbGainRL.gain.value = 0.0;
                            video.__tbGainRR.gain.value = 0.0;
                        } else if (mode === 'RIGHT_ONLY') {
                            // Vocal Guide (Right Channel to both ears)
                            video.__tbGainLL.gain.value = 0.0;
                            video.__tbGainLR.gain.value = 0.0;
                            video.__tbGainRL.gain.value = 1.0;
                            video.__tbGainRR.gain.value = 1.0;
                        } else if (mode === 'MONO_DOWNMIX') {
                            // Mono Downmix
                            video.__tbGainLL.gain.value = 0.5;
                            video.__tbGainLR.gain.value = 0.5;
                            video.__tbGainRL.gain.value = 0.5;
                            video.__tbGainRR.gain.value = 0.5;
                        } else {
                            // Stereo Full Mix
                            video.__tbGainLL.gain.value = 1.0;
                            video.__tbGainLR.gain.value = 0.0;
                            video.__tbGainRL.gain.value = 0.0;
                            video.__tbGainRR.gain.value = 1.0;
                        }
                    }
                }

                // =====================================================================
                // 6. CONTINUOUS WATCHDOG (SAFE & LIGHTWEIGHT)
                // =====================================================================
                if (!window.__ktbWatchdogActive) {
                    window.__ktbWatchdogActive = true;
                    setInterval(() => {
                        injectAdBlockCss();
                        skipActiveAd();
                        const v = document.querySelector('video');
                        if (v) {
                            if (!v.__tbConnected) {
                                updateDspRouting();
                            }
                            // Auto-resume background audio if paused by system/loss of focus when user didn't explicitly pause
                            if (v.paused && !v.ended && !window.__tbUserPaused) {
                                v.play().catch(() => {});
                            }
                        }
                        // Keep Web Audio context active in background
                        if (window.__tbAudioCtx && window.__tbAudioCtx.state === 'suspended') {
                            window.__tbAudioCtx.resume().catch(() => {});
                        }
                    }, 400);
                }

                // =====================================================================
                // 7. YOUTUBE NATIVE GESTURES (SWIPE UP -> FULLSCREEN, SWIPE DOWN -> EXIT)
                // =====================================================================
                window.tbEnterFullscreen = function() {
                    const fsBtn = document.querySelector('.fullscreen-icon') ||
                                  document.querySelector('button.fullscreen-button') ||
                                  document.querySelector('button[aria-label*="fullscreen" i]') ||
                                  document.querySelector('.ytp-fullscreen-button') ||
                                  document.querySelector('button[data-title-no-tooltip*="fullscreen" i]');
                    if (fsBtn) {
                        fsBtn.click();
                        return true;
                    }
                    const v = document.querySelector('video');
                    if (v) {
                        if (v.requestFullscreen) {
                            v.requestFullscreen().catch(() => {});
                        } else if (v.webkitRequestFullscreen) {
                            v.webkitRequestFullscreen();
                        } else if (v.webkitEnterFullscreen) {
                            v.webkitEnterFullscreen();
                        }
                        return true;
                    }
                    return false;
                };

                window.tbExitFullscreen = function() {
                    if (document.exitFullscreen) {
                        document.exitFullscreen().catch(() => {});
                    } else if (document.webkitExitFullscreen) {
                        document.webkitExitFullscreen();
                    }
                };

                if (!window.__ktbGestureListenerAdded) {
                    window.__ktbGestureListenerAdded = true;
                    let gestureStartX = 0;
                    let gestureStartY = 0;
                    let gestureStartTime = 0;
                    let gestureActive = false;

                    document.addEventListener('touchstart', (e) => {
                        if (!e.touches || e.touches.length !== 1) {
                            gestureActive = false;
                            return;
                        }
                        const t = e.touches[0];
                        const player = document.querySelector('#player-container-id') ||
                                       document.querySelector('.player-container') ||
                                       document.querySelector('#movie_player') ||
                                       document.querySelector('#player') ||
                                       document.querySelector('video');
                        let isTarget = false;
                        if (player) {
                            const r = player.getBoundingClientRect();
                            if (t.clientX >= r.left && t.clientX <= r.right &&
                                t.clientY >= r.top - 10 && t.clientY <= r.bottom + 20) {
                                isTarget = true;
                            }
                        }
                        if (!isTarget && (window.location.pathname.includes('/watch') || document.querySelector('video'))) {
                            if (t.clientY < 320) {
                                isTarget = true;
                            }
                        }
                        if (isTarget) {
                            gestureStartX = t.clientX;
                            gestureStartY = t.clientY;
                            gestureStartTime = Date.now();
                            gestureActive = true;
                        } else {
                            gestureActive = false;
                        }
                    }, { passive: true });

                    document.addEventListener('touchend', (e) => {
                        if (!gestureActive || !e.changedTouches || e.changedTouches.length === 0) return;
                        gestureActive = false;
                        const t = e.changedTouches[0];
                        const dx = t.clientX - gestureStartX;
                        const dy = t.clientY - gestureStartY;
                        const dt = Date.now() - gestureStartTime;

                        // Quick vertical swipe gesture (under 600ms, vertical movement > 40px, dy dominant)
                        if (dt < 600 && Math.abs(dy) > 40 && Math.abs(dy) > Math.abs(dx) * 1.25) {
                            if (dy < -40) {
                                // Swiped UP -> Enter Fullscreen!
                                window.tbEnterFullscreen();
                                if (window.AndroidBridge && window.AndroidBridge.onSwipeUp) {
                                    window.AndroidBridge.onSwipeUp();
                                }
                            } else if (dy > 40) {
                                // Swiped DOWN -> Exit Fullscreen if active
                                if (document.fullscreenElement || document.webkitFullscreenElement) {
                                    window.tbExitFullscreen();
                                    if (window.AndroidBridge && window.AndroidBridge.onSwipeDown) {
                                        window.AndroidBridge.onSwipeDown();
                                    }
                                }
                            }
                        }
                    }, { passive: true });
                }
            })();
        """.trimIndent()
    }

    private var blockedCounter = 0

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString()?.lowercase() ?: return super.shouldInterceptRequest(view, request)

        for (pattern in AD_HOST_PATTERNS) {
            if (url.contains(pattern)) {
                blockedCounter++
                onAdBlockedCountChanged?.invoke(blockedCounter)
                Log.d(TAG, "Blocked Ad Network Request ($blockedCounter): $url")
                return WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    ByteArrayInputStream(ByteArray(0))
                )
            }
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        injectDspAndAdBlock(view)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        injectDspAndAdBlock(view)
        view?.title?.let { title ->
            onPageTitleChanged?.invoke(title)
        }
    }

    fun injectDspAndAdBlock(view: WebView?) {
        try {
            view?.evaluateJavascript(INJECTED_DSP_AND_ADBLOCK_JS, null)
        } catch (e: Throwable) {
            Log.e(TAG, "Error injecting DSP/adblock JS", e)
        }
    }
}
