package dev.skyrimsemobile.launcher;

public final class SceneFramingTest {
    private static int checks;
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void close(float actual,float expected,float tolerance,String message){ok(Math.abs(actual-expected)<=tolerance,message+" "+actual+" != "+expected);}
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
        System.out.println("PASS: v2.5 compact viewport and camera framing: "+checks+" checks");
    }
}
