package dev.skyrimsemobile.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

/** Bounded, dependency-free Wavefront OBJ wireframe reader. No MTL, textures or animations. */
public final class ObjWireParser {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    public static final int MAX_VERTICES = 40000;
    public static final int MAX_EDGES = 30000;
    private static final int MAX_LINES = 120000;
    private ObjWireParser() {}

    public static final class Model {
        public final float[] segments; // xyz xyz for each line, normalized to [-1,1]
        public final int vertices;
        public final int edges;
        Model(float[] segments, int vertices, int edges) {
            this.segments=segments; this.vertices=vertices; this.edges=edges;
        }
    }

    public static Model parse(InputStream source) throws IOException {
        if(source == null) throw new IOException("Нет входного файла");
        ArrayList<float[]> positions = new ArrayList<>();
        LinkedHashSet<Long> edges = new LinkedHashSet<>();
        int lineNo=0;
        InputStream limited = new InputStream() {
            int count=0;
            @Override public int read() throws IOException {
                int c=source.read(); if(c>=0 && ++count>MAX_BYTES) throw new IOException("Файл больше 4 МиБ"); return c;
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                int n=source.read(b,off,Math.min(len, MAX_BYTES-count+1));
                if(n>0 && (count+=n)>MAX_BYTES) throw new IOException("Файл больше 4 МиБ");
                return n;
            }
        };
        BufferedReader input = new BufferedReader(new InputStreamReader(limited,StandardCharsets.UTF_8));
        String line;
        while((line=input.readLine())!=null){
            if(++lineNo>MAX_LINES) throw new IOException("Слишком много строк OBJ");
            line=line.trim();if(line.isEmpty()||line.startsWith("#")) continue;
            String[] fields=line.split("\\s+");
            if(fields.length>256) throw new IOException("Слишком длинная грань в строке "+lineNo);
            if(fields[0].equals("v")) {
                if(fields.length<4) throw new IOException("Неполная вершина в строке "+lineNo);
                if(positions.size()==MAX_VERTICES) throw new IOException("Слишком много вершин OBJ");
                float[] p=new float[3];
                for(int i=0;i<3;i++) {
                    try{p[i]=Float.parseFloat(fields[i+1]);}catch(NumberFormatException e){throw new IOException("Неверная вершина "+lineNo,e);}
                    if(!Float.isFinite(p[i])||Math.abs(p[i])>1e9f)throw new IOException("Неверные координаты в "+lineNo);
                }
                positions.add(p);
            } else if(fields[0].equals("f")||fields[0].equals("l")) {
                int min=fields[0].equals("f")?3:2;
                if(fields.length<min+1) throw new IOException("Неполная грань/линия в "+lineNo);
                int[] indices=new int[fields.length-1];
                for(int i=1;i<fields.length;i++) indices[i-1]=resolve(fields[i],positions.size(),lineNo);
                for(int i=0;i<indices.length-1;i++) addEdge(edges,indices[i],indices[i+1]);
                if(fields[0].equals("f")) addEdge(edges,indices[indices.length-1],indices[0]);
            }
        }
        if(positions.isEmpty()) throw new IOException("В OBJ нет вершин (v)");
        if(edges.isEmpty()) throw new IOException("В OBJ нет граней (f) или линий (l)");
        float[] low={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        float[] high={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(long edge:edges){
            int a=(int)(edge>>>32),b=(int)edge;
            for(int v:new int[]{a,b}) for(int i=0;i<3;i++) {
                low[i]=Math.min(low[i],positions.get(v)[i]); high[i]=Math.max(high[i],positions.get(v)[i]);
            }
        }
        float span=Math.max(high[0]-low[0],Math.max(high[1]-low[1],high[2]-low[2]));
        if(!(span>1e-10f)||!Float.isFinite(span)) throw new IOException("Размеры модели некорректны");
        float[] result=new float[edges.size()*6];int offset=0;
        for(long edge:edges){
            for(int v:new int[]{(int)(edge>>>32),(int)edge}) {
                float[] p=positions.get(v);
                for(int i=0;i<3;i++) result[offset++]=((p[i]-low[i])/span-0.5f)*2f;
            }
        }
        return new Model(result,positions.size(),edges.size());
    }
    private static int resolve(String token,int count,int line) throws IOException {
        try{
            String base=token.split("/",-1)[0];
            int n=Integer.parseInt(base);int index=n>0?n-1:count+n;
            if(n==0 || index<0 || index>=count) throw new IOException("Неверный индекс вершины в строке "+line);
            return index;
        }catch(NumberFormatException e){throw new IOException("Неверный индекс OBJ в строке "+line,e);}
    }
    private static void addEdge(LinkedHashSet<Long> set,int a,int b) throws IOException {
        if(a==b) return;
        int lo=Math.min(a,b),hi=Math.max(a,b);
        set.add(((long)lo<<32)|(hi&0xffffffffL));
        if(set.size()>MAX_EDGES) throw new IOException("Максимум "+MAX_EDGES+" рёбер для тестового рендера");
    }
}
