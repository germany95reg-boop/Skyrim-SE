package dev.skyrimsemobile.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A real-device three-stage frame-rate diagnostic without UI button presses.
 * The native Vulkan renderer stays alive: only its software FPS cap changes.
 * Frame counts are measured independently for each stage AFTER a warmup.
 * No Android dependencies, so phase transitions can be unit-tested on a JVM.
 */
final class FpsAutotestSequence {
    static final int[] CAPS={30,60,0};
    static final long WARMUP_MS=750;
    static final long MIN_SAMPLE_MS=1800;
    static final long STAGE_MS=3000;
    static final long MAX_TOTAL_MS=30000;

    static final class Result {
        final int cap;
        final long frames;
        final long elapsedMs;
        final double fps, nativeWindowFps;
        final boolean capConfirmed, frameCounterValid;

        Result(int cap,long frames,long elapsedMs,double nativeWindowFps,
               boolean capConfirmed,boolean frameCounterValid) {
            this.cap=cap;
            this.frames=frames;
            this.elapsedMs=elapsedMs;
            this.fps=elapsedMs>0 ? frames*1000.0/elapsedMs:0;
            this.nativeWindowFps=nativeWindowFps;
            this.capConfirmed=capConfirmed;
            this.frameCounterValid=frameCounterValid;
        }
        boolean passed(){
            return capConfirmed && frameCounterValid && frames>0 &&
                elapsedMs>=MIN_SAMPLE_MS && Double.isFinite(fps) && fps>0;
        }
        String label(){return cap==0?"Без ограничения":Integer.toString(cap);}
        String details(){
            return String.format(Locale.ROOT,
                "Лимит %s · фактически %.1f FPS · %d кадров за %.2f с · "+
                "Native FPS %.1f · подтверждение настройки: %s",
                label(),fps,frames,elapsedMs/1000.0,nativeWindowFps,
                capConfirmed?"да":"НЕТ");
        }
    }

    private final List<Result> results=new ArrayList<>();
    private int phase=0;
    private long startedMs=-1,baselineMs=-1,baselineFrames=-1;
    private long lastFrames=-1;

    int requestedCap(){return CAPS[Math.min(phase,CAPS.length-1)];}
    int stageNumber(){return Math.min(phase+1,CAPS.length);}
    int stageCount(){return CAPS.length;}
    boolean complete(){return phase>=CAPS.length;}
    List<Result> results(){return Collections.unmodifiableList(results);}

    /**
     * Called by the UI stats poll. Returns the completed stage, if any.
     * The caller then applies requestedCap() through nativeSetFpsCap.
     * The renderer must not be reset between stages.
     */
    Result sample(long nowMs,long totalFrames,int nativeCap,double nativeFps,boolean running){
        if(complete()||!running||totalFrames<=0)return null;
        if(startedMs<0){
            startedMs=nowMs;
            lastFrames=totalFrames;
            return null;
        }
        // Swapchain recreation can reset native frame counters. Discard the
        // partial window instead of producing a false FPS value.
        if(totalFrames<lastFrames){
            startedMs=nowMs;
            baselineMs=-1;
            baselineFrames=-1;
            lastFrames=totalFrames;
            return null;
        }
        lastFrames=totalFrames;
        long stageAge=nowMs-startedMs;
        if(stageAge<0){
            startedMs=nowMs;
            baselineMs=-1;
            baselineFrames=-1;
            return null;
        }
        if(baselineMs<0){
            if(stageAge>=WARMUP_MS){
                baselineMs=nowMs;
                baselineFrames=totalFrames;
            }
            return null;
        }
        if(stageAge<STAGE_MS||nowMs-baselineMs<MIN_SAMPLE_MS)return null;
        long frameDelta=totalFrames-baselineFrames;
        long timeDelta=nowMs-baselineMs;
        boolean sane=frameDelta>=0 && timeDelta>=MIN_SAMPLE_MS;
        Result result=new Result(requestedCap(),Math.max(0,frameDelta),
            timeDelta,nativeFps,nativeCap==requestedCap(),sane);
        results.add(result);
        phase++;
        startedMs=-1;
        baselineMs=-1;
        baselineFrames=-1;
        lastFrames=-1;
        return result;
    }
}
