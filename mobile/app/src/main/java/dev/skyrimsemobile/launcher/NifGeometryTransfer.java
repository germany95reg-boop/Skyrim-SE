package dev.skyrimsemobile.launcher;

/** One-shot in-memory handoff to the existing Vulkan line renderer. No disk writes or Binder-sized arrays. */
final class NifGeometryTransfer {
    private static Selected pending;
    static final class Selected {
        final ObjWireParser.Model model;
        final float[] surfaceTriangles;
        final String label, details;
        Selected(ObjWireParser.Model model,float[] surfaceTriangles,String label,String details){
            this.model=model;this.surfaceTriangles=surfaceTriangles;this.label=label;this.details=details;}
    }
    static synchronized void offer(ObjWireParser.Model model,float[] surfaceTriangles,String label,String details){
        pending=new Selected(model,surfaceTriangles,label,details);
    }
    static synchronized Selected take(){
        Selected result=pending;pending=null;return result;
    }
}
