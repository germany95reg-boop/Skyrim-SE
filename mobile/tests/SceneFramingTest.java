package dev.skyrimsemobile.launcher;

public final class SceneFramingTest {
    private static int checks;
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void close(float actual,float expected,float tolerance,String message){ok(Math.abs(actual-expected)<=tolerance,message+" "+actual+" != "+expected);}
    /** Independent shader-perspective check at intermediate yaw angles. */
    private static void fitsProjected(float[] segments,float yaw,float pitch,
                                      boolean spin,int w,int h,int top,int bottom){
        SceneFraming.Frame tight=SceneFraming.computeGeometry(
            segments,yaw,pitch,spin,w,h,top,bottom);
        SceneFraming.Frame old=SceneFraming.compute(
            SceneFraming.radius(segments),SceneFraming.horizontalRadius(segments),
            w,h,top,bottom);
        ok(tight.distance<=old.distance+0.0001f,
            "tight camera never farther than conservative sphere");
        ok(tight.distance>=2.7f&&tight.distance<=40f,
            "camera distance always bounded");
        close(tight.ndcYOffset,old.ndcYOffset,0.00001f,
              "same overlay-centered camera offset");
        close(tight.visibleFraction,old.visibleFraction,0.00001f,
              "same Android visible fraction");
        int steps=spin?96:1;
        for(int n=0;n<steps;n++){
            double a=yaw+(spin?2*Math.PI*n/steps:0);
            double cy=Math.cos(a),sy=Math.sin(a);
            double cp=Math.cos(pitch),sp=Math.sin(pitch);
            for(int i=0;i<segments.length;i+=3){
                double x=segments[i],y=segments[i+1],z=segments[i+2];
                double rx=cy*x+sy*z,rz=-sy*x+cy*z;
                double vy=cp*y-sp*rz,vz=sp*y+cp*rz;
                double depth=tight.distance+vz;
                ok(depth>0.5,"geometry must remain in front of near plane");
                double projectedX=3.2*rx/(Math.max((double)w/h,0.2)*depth);
                double projectedY=-3.2*vy/depth;
                ok(Math.abs(projectedX)<0.93,
                    "all rotated vertices fit horizontal safe area");
                ok(Math.abs(projectedY)<tight.visibleFraction*0.93,
                    "all rotated vertices fit vertical overlay-free area");
            }
        }
    }

    public static void main(String[] args) {
        float[] line={-1,-1,-1,1,1,1};
        close(SceneFraming.radius(line),(float)Math.sqrt(3),0.0001f,"radius");
        SceneFraming.Frame portrait=SceneFraming.compute(1.2f,1440,2915,260,1100);
        ok(portrait.ndcYOffset < 0,"bottom panel must shift model toward top");
        close(portrait.ndcYOffset,(260f-1100f)/2915f,0.0001f,"viewport center");
        ok(portrait.visibleFraction>.35f && portrait.visibleFraction<.60f,"content fraction");
        ok(portrait.distance>9.0f,"portrait should zoom out enough for a tall mesh");
        // A round sphere may be limited by portrait width even after collapsing.
        // A thin Skyrim prop has a much smaller XZ radius and benefits from it.
        float[] slim={-0.20f,-1f,0f,0.20f,1f,0.05f,
                      -0.15f,0.8f,-0.05f,0.10f,-0.8f,0.08f};
        float xz=SceneFraming.horizontalRadius(slim);
        ok(xz<SceneFraming.radius(slim)*0.4f,"narrow shape XZ footprint");
        SceneFraming.Frame slimFull=SceneFraming.compute(1.2f,xz,1440,2915,260,1100);
        SceneFraming.Frame compact=SceneFraming.compute(1.2f,xz,1440,2915,260,690);
        ok(compact.visibleFraction>slimFull.visibleFraction,"smaller panel increases visible viewport");
        ok(compact.distance<slimFull.distance,"slim model is fitted closer in compact mode");
        ok(slimFull.distance<portrait.distance,"slim shape uses narrower real width");
        close(SceneFraming.closer(compact.distance),compact.distance*0.82f,0.0001f,
              "one larger step moves camera in");
        ok(SceneFraming.closer(2.7f)==2.7f,"large mode respects near plane");
        ok(SceneFraming.closer(40f)<40f,"large mode stays within max");
        try{SceneFraming.horizontalRadius(new float[]{Float.NaN,0f,1f,1f,0f,1f});
            throw new AssertionError("NaN horizontal radius accepted");
        }catch(IllegalArgumentException expected){checks++;}
        try{SceneFraming.closer(Float.NaN);throw new AssertionError("NaN zoom accepted");}
        catch(IllegalArgumentException expected){checks++;}
        try{SceneFraming.closer(-2);throw new AssertionError("negative zoom accepted");}
        catch(IllegalArgumentException expected){checks++;}
        SceneFraming.Frame none=SceneFraming.compute(1.2f,1440,2915,0,0);
        close(none.ndcYOffset,0f,0.0001f,"no overlays");
        ok(portrait.distance>=none.distance,"overlays cannot reduce fit distance");
        SceneFraming.Frame landscape=SceneFraming.compute(1.2f,2915,1440,140,690);
        ok(landscape.distance>=2.7f && landscape.distance<=40,"landscape fit distance bounded");
        close(landscape.ndcYOffset,(140f-690)/1440f,.0001f,"landscape visual center");
        SceneFraming.Frame cube=SceneFraming.compute(SceneFraming.CUBE_RADIUS,1440,2915,260,1100);
        ok(cube.distance>=portrait.distance,"larger cube radius needs greater camera distance");
        SceneFraming.Frame cramped=SceneFraming.compute(1.0f,500,500,350,350);
        ok(cramped.distance>0 && cramped.distance<=40,"cramped visible region fit finite");
        try{SceneFraming.radius(new float[]{1f,Float.NaN,0,0,0,0});throw new AssertionError("expected NaN rejection");}
        catch(IllegalArgumentException expected){checks++;}
        try{SceneFraming.compute(1,0,500,0,0);throw new AssertionError("expected viewport rejection");}
        catch(IllegalArgumentException expected){checks++;}
        // Real screenshot-based issue: the v2.22 physical NIF occupies a
        // modest region of the screen (43x49 of the 192x256 PixelCopy).
        // New fit uses exact vertex perspective instead of a huge sphere.
        fitsProjected(slim,0f,0.42f,true,1440,2915,260,1100);
        SceneFraming.Frame slimSphere=SceneFraming.compute(
            SceneFraming.radius(slim),SceneFraming.horizontalRadius(slim),
            1440,2915,260,1100);
        SceneFraming.Frame slimTight=SceneFraming.computeGeometry(
            slim,0f,0.42f,true,1440,2915,260,1100);
        ok(slimTight.distance<slimSphere.distance,
            "tall narrow model must be visibly closer, not just report a number");
        fitsProjected(slim,0.6f,-0.4f,true,2915,1440,180,290);
        fitsProjected(line,0f,0.42f,true,1440,2915,260,1100);
        fitsProjected(line,1.2f,1.1f,false,1440,2915,260,690);
        fitsProjected(line,0.3f,-1.2f,true,2915,1440,180,420);
        try{SceneFraming.computeGeometry(line,Float.NaN,0.42f,true,
                                         1440,2915,260,1100);
            throw new AssertionError("NaN yaw accepted");
        }catch(IllegalArgumentException expected){checks++;}
        try{SceneFraming.computeGeometry(line,0f,Float.POSITIVE_INFINITY,false,
                                         1440,2915,260,1100);
            throw new AssertionError("infinite pitch accepted");
        }catch(IllegalArgumentException expected){checks++;}
        // A real v2.23 physical device had only 35% free viewport height.
        // The v2.24 automated GPU scene hides its unused multi-row controls,
        // leaving a one-line progress HUD. All regular preview controls stay.
        SceneFraming.Frame panelFit=SceneFraming.computeGeometry(
            slim,0f,0.42f,true,1440,2915,240,1100);
        SceneFraming.Frame hudFit=SceneFraming.computeGeometry(
            slim,0f,0.42f,true,1440,2915,240,104);
        ok(hudFit.visibleFraction>panelFit.visibleFraction+0.25f,
            "compact diagnostic HUD leaves substantially more display space");
        ok(hudFit.distance<panelFit.distance,
            "same real NIF vertices can appear larger without clipping");
        ok(hudFit.ndcYOffset>panelFit.ndcYOffset,
            "model center follows freed screen region");
        fitsProjected(slim,0f,0.42f,true,1440,2915,240,104);
        fitsProjected(slim,0.3f,0.85f,true,2915,1440,130,85);
        SceneFraming.Frame minimal=SceneFraming.compute(
            1.2f,1440,2915,240,104);
        ok(minimal.visibleFraction>0.80f,
            "small HUD gives over 80% viewport on representative tall device");
        System.out.println("PASS v2.24 compact GPU diagnostic viewport: "+checks+" checks");
    }
}
