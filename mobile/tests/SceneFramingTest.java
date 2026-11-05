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
        System.out.println("PASS: v1.3 adaptive camera framing: "+checks+" checks");
    }
}
