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
    public static Frame compute(float radius,int width,int height,int titleHeight,int panelHeight) {
        if(!(radius>0) || !Float.isFinite(radius) || width<=0 || height<=0)
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
        float safety=0.85f;
        float vertical=radius+3.2f*radius/(visibleFraction*safety);
        float horizontal=radius+3.2f*radius/(aspect*safety);
        float fit=Math.max(vertical,horizontal);
        return new Frame(Math.min(40f,Math.max(2.7f,fit)),offset,visibleFraction);
    }
}
