package main.java.TriangleCount;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.GenericOptionsParser;

import java.net.URI;

/**
 * a->b or b->a then a-b
 * 优点：用Text表示顶点，不受顶点大小的影响，鲁棒性更好，允许图中有重复边
 * 测试：
 * twitter: 127s 13082506
 * gplus: 291s  1073677742
 */

public class TriangleCountDriver {
    public final static String HDFS_PATH = "hdfs://192.168.100.103:9000/user/liujian/";
    public final static String OutDegreeStatPath = "temp/OutDegreeStat/";
    public final static String EdgeConvertPath = "temp/EdgeConvert/";
    public final static String GraphTriangleCountPath = "temp/GraphTriangleCount/";
    public final static int ReducerNum = 10;

    private final static String childResPath = HDFS_PATH + TriangleCountDriver.GraphTriangleCountPath;
    private static String outputPath = HDFS_PATH;

    public static void main(String[] args) throws Exception{
        long elapseTime = System.currentTimeMillis();

        Configuration conf = new Configuration();
        conf.set("mapreduce.reduce.memory.mb", "2048");  //设置reduce container的内存大小
        conf.set("mapreduce.reduce.java.opts", "-Xmx2048m");  //设置reduce任务的JVM参数
//        conf.set("mapreduce.map.java.opts", "-Xmx2048m");  //设置map任务的JVM参数

        String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();

        if (otherArgs.length != 2) {
            System.err.println("Usage: hadoop jar TriangleCount.jar TriangleCountDriver " +
                    "input/graphTriangleCount/twitter_graph_v2.txt output");
            System.exit(2);
        }
        String[] forGB = {otherArgs[0], ""};

        forGB[1] = OutDegreeStatPath;
        OutDegreeStat.main(forGB);

        forGB[1] = EdgeConvertPath;
        EdgeConvert.main(forGB);

        forGB[0] = EdgeConvertPath;
        forGB[1] = GraphTriangleCountPath;
        GraphTriangleCount.main(forGB);

        outputPath += otherArgs[1] + "/part-r-00000";
        long triangleSum = 0;
        FileSystem fs = FileSystem.get(new URI(childResPath), conf);
        for (FileStatus fst: fs.listStatus(new Path(childResPath))) {
            if (!fst.getPath().getName().startsWith("_")) {
                SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.file(fst.getPath()));
                Text key = new Text();
                LongWritable value = new LongWritable();
                reader.next(key, value);
                triangleSum += value.get();
                reader.close();
            }
        }
        fs = FileSystem.get(new URI(outputPath), conf);
        FSDataOutputStream outputStream = fs.create(new Path(outputPath));
        outputStream.writeChars("TriangleSum = " + triangleSum + "\n");
        outputStream.close();
        elapseTime = System.currentTimeMillis() - elapseTime;
        System.out.println("TriangleSum = " + triangleSum);
        System.out.println("Timeused: " + elapseTime/1000 + "s");
        System.exit(0);
    }
}
