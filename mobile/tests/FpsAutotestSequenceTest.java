package dev.skyrimsemobile.launcher;

import java.util.List;

/** Synthetic frame-counter windows for the on-phone 30/60/unlimited sequence. */
public final class FpsAutotestSequenceTest {
    private static int checked;
    private static void check(boolean valid,String message){
        checked++;
        if(!valid)throw new AssertionError(message);
    }
    private static void close(double actual,double expected,double tolerance,String message){
        check(Math.abs(actual-expected)<=tolerance,message+" "+actual+" vs "+expected);
    }
    public static void main(String[] args){
        FpsAutotestSequence p=new FpsAutotestSequence();
        check(p.requestedCap()==30,"initial real-GPU cap 30");
        check(p.stageCount()==3,"three phases");
        check(p.sample(100,0,30,0,false)==null,"no frames means not started");
        check(p.sample(1000,1,30,30,true)==null,"start first phase");
        check(p.sample(1800,24,30,29,true)==null,"discard warm-up");
        check(p.sample(3200,66,30,30,true)==null,"not enough sample time");
        FpsAutotestSequence.Result a=p.sample(4100,90,30,29.9,true);
        check(a!=null&&a.cap==30&&a.passed(),"30-FPS stage");
        check(a.frames==66&&a.elapsedMs==2300,"30-FPS delta");
        close(a.fps,66*1000.0/2300,0.01,"30-FPS measured by delta frames / wall time");
        check(p.requestedCap()==60,"automatic next stage 60");
        check(p.sample(4100,90,60,60,true)==null,"start 60-FPS");
        check(p.sample(4900,137,60,59,true)==null,"60-FPS warmup");
        FpsAutotestSequence.Result b=p.sample(7200,275,60,59.3,true);
        check(b!=null&&b.passed(),"60-FPS stage");
        check(b.frames==138,"60-FPS stage independent of prior frames");
        close(b.fps,60.0,0.01,"60-FPS actual measurement");
        check(p.requestedCap()==0,"automatic next stage uncapped");
        check(p.sample(7200,275,0,60,true)==null,"start uncapped stage");
        check(p.sample(8000,336,0,62,true)==null,"uncapped warm-up");
        FpsAutotestSequence.Result c=p.sample(10300,475,0,59,true);
        check(c!=null&&c.passed(),"uncapped phase");
        check(c.cap==0&&c.capConfirmed,"uncapped is native cap zero");
        close(c.fps,139*1000.0/2300,0.01,"uncapped measured, not invented 120FPS");
        check(p.complete(),"all stages finished");
        check(p.results().size()==3,"three independent per-cap samples");
        check(p.sample(12000,700,0,60,true)==null,"no samples after completion");
        check(a.details().contains("Лимит 30"),"30 label");
        check(c.details().contains("Лимит Без ограничения"),"uncapped label");

        FpsAutotestSequence wrong=new FpsAutotestSequence();
        wrong.sample(1000,5,30,30,true);
        wrong.sample(1900,30,30,30,true);
        FpsAutotestSequence.Result mismatch=wrong.sample(4300,100,60,59,true);
        check(mismatch!=null&&!mismatch.passed(),"native cap mismatch fails");
        check(!mismatch.capConfirmed,"cap mismatch tracked");

        FpsAutotestSequence reset=new FpsAutotestSequence();
        reset.sample(1000,20,30,29,true);
        reset.sample(1900,45,30,30,true);
        check(reset.sample(2500,2,30,30,true)==null,"recreated swapchain resets counters");
        check(reset.sample(3400,29,30,30,true)==null,"restarted sample");
        FpsAutotestSequence.Result afterReset=reset.sample(5700,98,30,30,true);
        check(afterReset!=null&&afterReset.passed(),"recover accurate FPS after reset");
        check(afterReset.frames==69,"do not count pre-reset frames");

        FpsAutotestSequence idle=new FpsAutotestSequence();
        check(idle.sample(9000,100,30,30,false)==null,"do not count nonrunning Vulkan");
        check(idle.stageNumber()==1,"stage unchanged when not running");

        System.out.println("PASS v2.16 physical GPU FPS matrix: "+checked+" checks");
    }
}
