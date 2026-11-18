package com.xservernull.archivist.client.shader;

import org.joml.Vector3f;

/**
 * One dimension's screen look. Everything starts neutral (no effect), so a dimension only sets what it
 * needs, with the chained methods below. Read by {@link PostFx}, listed in {@link Looks}.
 */
public final class Look {
    // flashlight: only a circle at the screen centre and things near you stay lit. darkness 0 = off
    public float darkness, beamFull = 10f, beamEnd = 11f, reachFull = 1e5f, reachEnd = 2e5f;
    // colour grade: tint multiplies, saturation 0 = grey, contrast 1 = unchanged
    public final Vector3f tint = new Vector3f(1f, 1f, 1f);
    public float saturation = 1f, contrast = 1f;
    // haze: far terrain fades to a colour between fogStart and fogEnd blocks. fogEnd 0 = off
    public final Vector3f fogColor = new Vector3f();
    public float fogStart, fogEnd;
    // frame: darker edges, film grain (0 = off)
    public float vignette, grain;
    // ambient occlusion: darker creases and corners. aoStrength 0 = off, radius and fade distance in blocks
    public float aoStrength, aoRadius = 1f, aoFade = 48f;
    // retro: the picture is copied down to this fraction of the screen size (0.15 = 15%) and back up with
    // nearest filtering. No shader runs for it, so a retro look ignores every other setting except sky. 0 = off.
    public float pixel;
    // flat sky: the sky and the far fog get this colour (set by FlatSky). null = vanilla
    public Vector3f sky;
    // bloom: bright things (not the sky) glow. bloomStrength 0 = off, threshold = brightness 0..1 where glow starts
    public float bloomStrength, bloomThreshold = 0.8f;
    // time-driven effects (read from GameTime, so they stand still in Quiet Place). 0 = off
    // flicker: lamp-like brightness wobble, amplitude (0.05 = about 5%). breathe: the vignette swells and fades
    // (0.35 = its strength swings by 35%). warp: far terrain smears sideways (1 = about 2% of the screen width)
    public float flicker, breathe, warp;

    public Look light(float darkness, float beamFull, float beamEnd, float reachFull, float reachEnd) {
        this.darkness = darkness;
        this.beamFull = beamFull;
        this.beamEnd = beamEnd;
        this.reachFull = reachFull;
        this.reachEnd = reachEnd;
        return this;
    }

    public Look grade(float r, float g, float b, float saturation, float contrast) {
        tint.set(r, g, b);
        this.saturation = saturation;
        this.contrast = contrast;
        return this;
    }

    public Look haze(float r, float g, float b, float start, float end) {
        fogColor.set(r, g, b);
        fogStart = start;
        fogEnd = end;
        return this;
    }

    public Look ao(float strength, float radius, float fade) {
        aoStrength = strength;
        aoRadius = radius;
        aoFade = fade;
        return this;
    }

    public Look retro(float pixel) {
        this.pixel = pixel;
        return this;
    }

    public Look sky(float r, float g, float b) {
        sky = new Vector3f(r, g, b);
        return this;
    }

    public Look bloom(float strength, float threshold) {
        bloomStrength = strength;
        bloomThreshold = threshold;
        return this;
    }

    public Look flicker(float amplitude) {
        flicker = amplitude;
        return this;
    }

    public Look breathe(float amplitude) {
        breathe = amplitude;
        return this;
    }

    public Look warp(float strength) {
        warp = strength;
        return this;
    }

    public Look copyFrom(Look o) {
        darkness = o.darkness;
        beamFull = o.beamFull;
        beamEnd = o.beamEnd;
        reachFull = o.reachFull;
        reachEnd = o.reachEnd;
        tint.set(o.tint);
        saturation = o.saturation;
        contrast = o.contrast;
        fogColor.set(o.fogColor);
        fogStart = o.fogStart;
        fogEnd = o.fogEnd;
        vignette = o.vignette;
        grain = o.grain;
        aoStrength = o.aoStrength;
        aoRadius = o.aoRadius;
        aoFade = o.aoFade;
        pixel = o.pixel;
        sky = o.sky;
        bloomStrength = o.bloomStrength;
        bloomThreshold = o.bloomThreshold;
        flicker = o.flicker;
        breathe = o.breathe;
        warp = o.warp;
        return this;
    }

    public Look frame(float vignette, float grain) {
        this.vignette = vignette;
        this.grain = grain;
        return this;
    }
}