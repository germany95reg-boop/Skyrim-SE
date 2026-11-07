package dev.skyrimsemobile.launcher;

/** Pure math for fitting a bounded wireframe to the visible area between the title and controls. */
public final class SceneFraming {
    private SceneFraming() {}
    public static final float CUBE_RADIUS = (float)Math.sqrt(3.0);
    public static final class Frame {
        public final float distance, ndcYOffset, visibleFraction;
        Frame(float distance, float offset, float fraction) {
            this.distance=distance; this.ndcYOffset=offset; this.visibleFraction=fraction;
        }
    }
    public static float radius(float[] segments) {
        if(segments==null || segments.length<6 || segments.length%6!=0)
            throw new IllegalArgumentException("Неверная геометрия");
        double maxSquared=0;
        for(int i=0;i<segments.length;i+=3) {
            double x=segments[i], y=segments[i+1], z=segments[i+2];
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))
                throw new IllegalArgumentException("Некорректная координата");
            maxSquared=Math.max(maxSquared,x*x+y*y+z*z);
        }
        return Math.max(0.15f,(float)Math.sqrt(maxSquared));
    }
    /** One bounded camera step towards the model; "Вписать" resets to safe fit. */
    public static float closer(float distance){
        if(!Float.isFinite(distance)||distance<=0)
            throw new IllegalArgumentException("Неверная дистанция камеры");
        return Math.max(2.7f,Math.min(40f,distance*0.82f));
    }
    /** Maximum XZ radius across every yaw; narrower than the 3D sphere for tall shapes.
     * The Vulkan shader rotates around Y, then pitches around X, so camera-facing
     * horizontal X is always bounded by hypot(original X,original Z). */
    public static float horizontalRadius(float[] segments) {
        if(segments==null || segments.length<6 || segments.length%6!=0)
            throw new IllegalArgumentException("Неверная геометрия");
        double maxSquared=0;
        for(int i=0;i<segments.length;i+=3){
            double x=segments[i],y=segments[i+1],z=segments[i+2];
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))
                throw new IllegalArgumentException("Некорректная координата");
            maxSquared=Math.max(maxSquared,x*x+z*z);
        }
        return Math.max(0.05f,(float)Math.sqrt(maxSquared));
    }
    /**
     * Geometry-aware auto-fit for real NIF/OBJ meshes.
     *
     * Unlike the previous enclosing-sphere formula, this evaluates the SAME
     * yaw/pitch transformation and perspective scale as textured.vert /
     * textured_safe.vert, for actual mesh vertices. During auto-rotation we
     * sample 24 directions around Y and reserve 13% viewport padding; the
     * margin exceeds the possible 7.5 degree between-sample extremum.
     *
     * The conservative sphere fit is retained as a safe upper bound. Manual
     * zoom still works unchanged; this method is for "Вписать" and startup.
     */
    public static Frame computeGeometry(float[] segments,float yaw,float pitch,
                                        boolean autoRotate,int width,int height,
                                        int titleHeight,int panelHeight){
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch))
            throw new IllegalArgumentException("Некорректный угол камеры");
        float radius=radius(segments);
        Frame sphere=compute(radius,horizontalRadius(segments),
                             width,height,titleHeight,panelHeight);
        final int samples=autoRotate?24:1;
        final double cp=Math.cos(pitch),sp=Math.sin(pitch);
        final double aspect=Math.max(0.2,(double)width/height);
        // Shader NDC y: -3.2*y/(distance+z) + focusOffsetY.
        // The visible region between overlays has this half-extent.
        final double safeVertical=Math.max(0.01,sphere.visibleFraction*0.87);
        final double safeHorizontal=0.87;
        double required=2.7;
        for(int sample=0;sample<samples;sample++){
            double angle=yaw+(autoRotate?sample*(2*Math.PI/samples):0);
            double cy=Math.cos(angle),sy=Math.sin(angle);
            for(int i=0;i<segments.length;i+=3){
                double x=segments[i],y=segments[i+1],z=segments[i+2];
                double rx=cy*x+sy*z;
                double rz=-sy*x+cy*z;
                double vy=cp*y-sp*rz;
                double vz=sp*y+cp*rz;
                // 3.2 perspective scale is used by the real vertex shader.
                // Require 1.0 units of positive W, then bound BOTH axes.
                required=Math.max(required,1.0-vz);
                required=Math.max(required,3.2*Math.abs(rx)/
                    (aspect*safeHorizontal)-vz);
                required=Math.max(required,3.2*Math.abs(vy)/
                    safeVertical-vz);
            }
        }
        if(!Double.isFinite(required))
            throw new IllegalArgumentException("Переполнение дистанции камеры");
        // Never zoom out beyond the already validated sphere envelope.
        double distance=Math.min(sphere.distance,required);
        return new Frame((float)Math.max(2.7,Math.min(40.0,distance)),
                         sphere.ndcYOffset,sphere.visibleFraction);
    }

    /** Compatibility overload: a sphere needs its entire radius in both axes. */
    public static Frame compute(float radius,int width,int height,int titleHeight,int panelHeight){
        return compute(radius,radius,width,height,titleHeight,panelHeight);
    }
    public static Frame compute(float radius,float horizontalRadius,
                                int width,int height,int titleHeight,int panelHeight) {
        if(!(radius>0) || !Float.isFinite(radius) ||
           !(horizontalRadius>=0) || !Float.isFinite(horizontalRadius) ||
           width<=0 || height<=0)
            throw new IllegalArgumentException("Неверный размер модели или экрана");
        int top=Math.max(0,Math.min(titleHeight,height));
        int bottom=Math.max(0,Math.min(panelHeight,height));
        // Leave at least 18% as a fallback for small landscape screens.
        int available=Math.max((int)(height*0.18f),height-top-bottom);
        float visibleFraction=(float)available/height;
        float aspect=(float)width/height;
        // Vulkan viewport maps NDC y=-1 to the top, so negative moves the model upwards.
        float offset=Math.max(-0.82f,Math.min(0.82f,(float)(top-bottom)/height));
        if(top+bottom>height*0.82f) offset=Math.max(-0.82f,Math.min(0.82f,offset));
        // The shader perspective scale is 3.2 / distance. Bound the entire rotating sphere.
        // Keep 15% padding and prevent vertices near the camera from escaping the frustum.
        float safety=0.90f; // 10% sphere padding; preserves fit while enlarging the model.
        float vertical=radius+3.2f*radius/(visibleFraction*safety);
        // Keep the full 3D sphere as near-plane safety; only the screen's X
        // requirement uses the real XZ half-width. Works for every yaw/pitch.
        float horizontal=radius+3.2f*Math.min(radius,Math.max(0.05f,horizontalRadius))/
                         (aspect*safety);
        float fit=Math.max(vertical,horizontal);
        return new Frame(Math.min(40f,Math.max(2.7f,fit)),offset,visibleFraction);
    }
}
